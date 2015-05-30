
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;

import org.dellroad.msrp.msg.ByteRange;
import org.dellroad.msrp.msg.FailureReport;
import org.dellroad.msrp.msg.Header;
import org.dellroad.msrp.msg.MsrpHeaders;
import org.dellroad.msrp.msg.MsrpInputParser;
import org.dellroad.msrp.msg.MsrpMessage;
import org.dellroad.msrp.msg.MsrpRequest;
import org.dellroad.msrp.msg.MsrpResponse;
import org.dellroad.msrp.msg.ProtocolException;
import org.dellroad.msrp.msg.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents one MSRP session.
 */
public class Session {

    private static final int MAX_CONTENT_LENGTH = MsrpInputParser.DEFAULT_MAX_CONTENT_LENGTH;
    private static final long MAX_TRANSACTION_AGE_MILLIS = 30000L;                              // 30 seconds
    private static final long MAX_MESSAGE_IDLE_TIME_MILLIS = 90000L;                            // 90 seconds

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Msrp msrp;
    private final MsrpUri localURI;
    private final MsrpUri remoteURI;
    private final Endpoint activeEndpoint;
    private final SessionListener listener;
    private final Executor callbackExecutor;
    private final long startTime = System.nanoTime();

    private final TreeMap<String, InputChunks> inputChunks = new TreeMap<>();                   // key is message ID
    private final TreeMap<String, OutputChunks> outputChunks = new TreeMap<>();                 // key is message ID
    private final TreeMap<String, OutputTransaction> outputTransactions = new TreeMap<>();      // key is transaction ID
    private final ArrayDeque<MsrpMessage> outputQueue = new ArrayDeque<>();

    private Connection connection;
    private boolean closed;

    Session(Msrp msrp, MsrpUri localURI, MsrpUri remoteURI, Endpoint activeEndpoint,
      SessionListener listener, Executor callbackExecutor) {
        this.msrp = msrp;
        this.localURI = localURI;
        this.remoteURI = remoteURI;
        this.activeEndpoint = activeEndpoint;
        this.listener = listener;
        this.callbackExecutor = callbackExecutor;
    }

    /**
     * Get local URI.
     */
    public MsrpUri getLocalUri() {
        return this.localURI;
    }

    /**
     * Get remote URI.
     */
    public MsrpUri getRemoteUri() {
        return this.remoteURI;
    }

    @Override
    public String toString() {
        return "Session[localURI=" + this.localURI + ",remoteURI=" + this.remoteURI + "]";
    }

// Public API

    /**
     * Close this session.
     *
     * <p>
     * Any {@link FailureListener}s associated with queued messages will be notified.
     * This method is idempotent.
     * </p>
     *
     * @param cause error that occurred, if any, otherwise null
     * @return true if this instance was closed, false if this instance was already closed
     */
    public boolean close(final Exception cause) {
        synchronized (this.msrp) {
            if (this.closed)
                return false;
            if (this.log.isDebugEnabled())
                this.log.debug("closing " + this);
            try {
                this.flushOutputQueue();            // to ensure any failure responses get sent
            } catch (IOException e) {
                // ignore
            }
            for (OutputChunks chunks : this.outputChunks.values()) {
                chunks.close();
                if (chunks.hasNext()) {
                    chunks.notifyFailure(this, this.callbackExecutor,
                      new Status(MsrpConstants.RESPONSE_CODE_SESSION_DOES_NOT_EXIST, "Session closed"));
                }
            }
            this.inputChunks.clear();
            this.outputChunks.clear();
            this.outputTransactions.clear();
            this.outputQueue.clear();
            this.closed = true;
            this.msrp.handleSessionClosed(this);

            // Notify listener
            this.callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Session.this.listener.sessionClosed(Session.this, cause);
                    } catch (ThreadDeath t) {
                        throw t;
                    } catch (Throwable t) {
                        Session.this.log.error("error in listener notification", t);
                    }
                }
            });
            return true;
        }
    }

    /**
     * Enqueue an outgoing message specified as a {@code byte[]} array.
     *
     * @param content message content
     * @param contentType content type
     * @param headers other headers (MIME and/or extension), or null for none
     * @param reportListener success and/or failure listener, or null for none
     * @return unique message ID, or null if this instance is closed
     * @throws IllegalArgumentException if {@code content} or {@code contentType} is null
     * @throws IllegalArgumentException if {@code headers} contains an invalid header name or value
     */
    public String send(byte[] content, String contentType, Iterable<? extends Header> headers, ReportListener reportListener) {
        if (content == null)
            throw new IllegalArgumentException("null content");
        return this.doSend(new ByteArrayInputStream(content), content.length, contentType, headers, reportListener);
    }

    /**
     * Enqueue an outgoing message specified as an {@link InputStream}.
     * This method will handle closing the provided {@code input}.
     *
     * @param input message content input; will be eventually closed by this method
     * @param size size of input, or -1 if unknown; if positive and input is longer than this, it will be truncated
     * @param contentType content type
     * @param headers other headers (MIME and/or extension), or null for none
     * @param reportListener success and/or failure listener, or null for none
     * @return unique message ID, or null if this instance is closed
     * @throws IllegalArgumentException if {@code input} or {@code contentType} is null
     * @throws IllegalArgumentException if {@code size} is less than -1
     * @throws IllegalArgumentException if {@code headers} contains an invalid header name or value
     */
    public String send(InputStream input, int size, String contentType,
      Iterable<? extends Header> headers, ReportListener reportListener) {
        if (input == null)
            throw new IllegalArgumentException("null input");
        return this.doSend(input, size, contentType, headers, reportListener);
    }

    /**
     * Enqueue an outgoing message with no content.
     *
     * @param headers other headers (MIME and/or extension), or null for none
     * @param reportListener success and/or failure listener, or null for none
     * @return unique message ID, or null if this instance is closed
     * @throws IllegalArgumentException if {@code input} or {@code contentType} are not both null or both not-null
     * @throws IllegalArgumentException if {@code size} is less than -1 or {@code input} is null and size is not -1
     * @throws IllegalArgumentException if {@code headers} contains an invalid header name or value
     */
    public String send(Iterable<? extends Header> headers, ReportListener reportListener) {
        return this.doSend(null, -1, null, headers, reportListener);
    }

    /**
     * Cancel an outgoing message previously sent.
     *
     * @param messageId message ID returned by {@code send()}
     * @throws IllegalArgumentException if {@code messageId} is null
     * @return true if message was canceled, false if message has already been completely sent
     */
    public boolean cancel(String messageId) {
        if (messageId == null)
            throw new IllegalArgumentException("null messageId");
        synchronized (this.msrp) {
            final OutputChunks chunks = this.outputChunks.get(messageId);
            if (chunks == null)
                return false;
            chunks.close();
            return chunks.isAborted();
        }
    }

    /**
     * Enqueue an outgoing success report.
     *
     * @param toPath path to the peer; should equal the {@code From-Path} from the received message
     * @param messageId the {@code Message-ID} from the received message
     * @param status success status, or null for default {@code 200 Message Delivered}
     * @param byteRange byte range successfully received
     * @return true if report was sent, false if report could not be sent because this instance is closed
     * @throws IllegalArgumentException if {@code toPath} is null or empty
     * @throws IllegalArgumentException if {@code messageId} is null or invalid
     * @throws IllegalArgumentException if {@code byteRange} is null
     */
    public boolean sendSuccessReport(List<MsrpUri> toPath, String messageId, ByteRange byteRange, Status status) {
        if (toPath == null || toPath.isEmpty() || toPath.get(0) == null)
            throw new IllegalArgumentException("null/empty toPath");
        if (messageId == null)
            throw new IllegalArgumentException("null messageId");
        if (byteRange == null)
            throw new IllegalArgumentException("null byteRange");
        if (status == null)
            status = new Status(MsrpConstants.RESPONSE_CODE_OK, "Message delivered");
        synchronized (this.msrp) {
            if (this.closed)
                return false;
            this.enqueueReport(toPath, messageId, status, byteRange);
        }
        return true;
    }

    /**
     * Enqueue an outgoing failure report.
     *
     * @param toPath path to the peer; should equal the {@code From-Path} from the received message
     * @param messageId the {@code Message-ID} from the received message
     * @param status failure status
     * @return true if report was sent, false if report could not be sent because this instance is closed
     * @throws IllegalArgumentException if {@code toPath} is null or empty
     * @throws IllegalArgumentException if {@code messageId} is null or invalid
     * @throws IllegalArgumentException if {@code status} is null
     */
    public boolean sendFailureReport(List<MsrpUri> toPath, String messageId, Status status) {
        if (toPath == null || toPath.isEmpty() || toPath.get(0) == null)
            throw new IllegalArgumentException("null/empty toPath");
        if (messageId == null)
            throw new IllegalArgumentException("null messageId");
        if (status == null)
            throw new IllegalArgumentException("null status");
        synchronized (this.msrp) {
            if (this.closed)
                return false;
            this.enqueueReport(toPath, messageId, status, null);
        }
        return true;
    }

// Internal API

    String doSend(InputStream input, int size, String contentType,
      Iterable<? extends Header> headers, ReportListener reportListener) {
        synchronized (this.msrp) {

            // Sanity check
            if (this.closed)
                return null;

            // Enqueue output message
            final OutputChunks chunks = new OutputChunks(this.localURI,
              this.remoteURI, input, size, contentType, headers, reportListener);
            final String messageId = chunks.getMessageId();
            this.outputChunks.put(messageId, chunks);

            // Wakeup MSRP thread so it will invoke performHousekeeping()
            this.msrp.wakeup();

            // Done
            return messageId;
        }
    }

    Connection getConnection() {
        return this.connection;
    }
    void setConnection(Connection connection) {
        this.connection = connection;
    }

// Incoming Message Handling

    // Handle an incoming {@link MsrpMessage} received on this session.
    void handleMessage(MsrpMessage msg) {

        // Sanity check
        if (msg == null)
            throw new IllegalArgumentException("null msg");
        if (this.closed)
            throw new IllegalStateException("session is closed");

        // Handle request or response
        if (msg instanceof MsrpRequest)
            this.handleRequest((MsrpRequest)msg);
        else if (msg instanceof MsrpResponse)
            this.handleResponse((MsrpResponse)msg);
        else
            this.log.error("Session.handleInput(): ignoring unknown message of type " + msg.getClass().getName());
    }

    // Handle request
    private void handleRequest(MsrpRequest request) {
        switch (request.getMethod()) {
        case MsrpConstants.METHOD_SEND:
            this.handleSend(request);
            break;
        case MsrpConstants.METHOD_REPORT:
            this.handleReport(request);
            break;
        default:
            if (!FailureReport.NO.equals(request.getHeaders().getFailureReport())) {
                this.outputQueue.add(Session.createMsrpResponse(request,
                  MsrpConstants.RESPONSE_CODE_UNKNOWN_METHOD, "Unknown method `" + request.getMethod() + "'"));
            }
            return;
        }
    }

    // Handle SEND request
    private void handleSend(MsrpRequest request) {

        // Get/create input message
        final MsrpHeaders headers = request.getHeaders();
        final String messageId = headers.getMessageId();
        InputChunks chunks0 = this.inputChunks.get(messageId);
        if (chunks0 == null) {
            chunks0 = new InputChunks(messageId, this.msrp.getMaxContentLength());
            this.inputChunks.put(messageId, chunks0);
        }
        final InputChunks chunks = chunks0;

        // Process request
        final boolean complete;
        try {
            complete = chunks.handleSend(request);
        } catch (ProtocolException e) {
            if (!FailureReport.NO.equals(headers.getFailureReport())) {
                this.outputQueue.add(Session.createMsrpResponse(request,
                  MsrpConstants.RESPONSE_CODE_BAD_REQUEST, "Protocol error: " + e.getMessage()));
            }
            this.close(e);
            return;
        }

        // Reply to transaction
        if (!FailureReport.NO.equals(headers.getFailureReport()) && !FailureReport.PARTIAL.equals(headers.getFailureReport()))
            this.outputQueue.add(Session.createMsrpResponse(request, MsrpConstants.RESPONSE_CODE_OK, "OK"));

        // Is message aborted?
        if (chunks.isAborted()) {
            this.inputChunks.remove(messageId);
            return;
        }

        // Is message still incomplete?
        if (!complete)
            return;

        // Remove from incoming messages
        this.inputChunks.remove(messageId);

        // Notify listener of reception of complete message
        final byte[] content = chunks.getContent();
        final TreeSet<Header> combinedHeaders = new TreeSet<Header>(Header.SORT_BY_NAME);
        combinedHeaders.addAll(headers.getMimeHeaders());
        combinedHeaders.addAll(headers.getExtensionHeaders());
        this.callbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Session.this.listener.sessionReceivedMessage(Session.this, chunks.getFromPath(), messageId,
                      content, headers.getContentType(), combinedHeaders, chunks.isSuccessReport(),
                      FailureReport.YES.equals(chunks.getFailureReport()));
                } catch (ThreadDeath t) {
                    throw t;
                } catch (Throwable t) {
                    Session.this.log.error("error in listener notification", t);
                }
            }
        });
    }

    // Handle REPORT request
    private void handleReport(MsrpRequest request) {

        // Extract info
        final MsrpHeaders requestHeaders = request.getHeaders();
        final String messageId = requestHeaders.getMessageId();
        if (messageId == null) {
            this.log.warn("rec'd REPORT with no message ID in session from " + this.remoteURI + ", ignoring");
            return;
        }
        final Status status = requestHeaders.getStatus();
        if (status == null) {
            this.log.warn("rec'd REPORT with no Status in session from " + this.remoteURI + ", ignoring");
            return;
        }
        final boolean success = status.getNamespace() == 0 && status.getCode() / 100 == 2;
        final ByteRange byteRange = requestHeaders.getByteRange();
        if (success && byteRange == null) {
            this.log.warn("rec'd success REPORT with no ByteRange from " + this.remoteURI + ", ignoring");
            return;
        }

        // Find outgoing message
        final OutputChunks chunks = this.outputChunks.get(messageId);
        if (chunks == null)
            return;

        // Notify listener
        if (success)
            chunks.notifySuccess(this, this.callbackExecutor, byteRange);
        else
            chunks.notifyFailure(this, this.callbackExecutor, status);
    }

    // Handle transaction response
    private void handleResponse(MsrpResponse response) {

        // Find the corresponding transaction and output chunks
        final OutputTransaction transaction = this.outputTransactions.remove(response.getTransactionId());
        if (transaction == null)
            return;
        final OutputChunks chunks = transaction.getOutputChunks();

        // Success is easy :-)
        if (response.getCode() < 300)
            return;

        // Notify failure listener, if any
        chunks.notifyFailure(this, this.callbackExecutor, response.toStatus());

        // Interrupt output chunks
        chunks.close();

        // Handle specific codes
        switch (response.getCode()) {
        case MsrpConstants.RESPONSE_CODE_SESSION_DOES_NOT_EXIST:
        case MsrpConstants.RESPONSE_CODE_SESSION_ALREADY_BOUND:
            this.close(new Exception("rec'd error from remote: " + response.getResultString()));
            break;
        default:
            break;
        }
    }

    /**
     * Create a {@link MsrpResponse} corresponding to the given request with the given error code and comment.
     */
    static MsrpResponse createMsrpResponse(MsrpRequest request, int code, String comment) {

        // Sanity check
        if (request == null)
            throw new IllegalArgumentException("null request");
        new Status(code, comment);

        // Build response
        final MsrpHeaders requestHeaders = request.getHeaders();
        final MsrpHeaders responseHeaders = new MsrpHeaders();
        if (MsrpConstants.METHOD_SEND.equals(request.getMethod()))
            responseHeaders.getToPath().add(requestHeaders.getFromPath().get(0));
        else
            responseHeaders.getToPath().addAll(requestHeaders.getFromPath());
        responseHeaders.getFromPath().add(requestHeaders.getToPath().get(0));
        return new MsrpResponse(request.getTransactionId(), code, comment, responseHeaders);
    }

    private void enqueueReport(List<MsrpUri> toPath, String messageId, Status status, ByteRange byteRange) {
        final MsrpHeaders headers = new MsrpHeaders();
        headers.getToPath().addAll(toPath);
        headers.getFromPath().add(Session.this.localURI);
        headers.setMessageId(messageId);
        headers.setStatus(status);
        if (byteRange != null)
            headers.setByteRange(byteRange);
        this.outputQueue.add(new MsrpRequest(MsrpMessage.randomId(), MsrpConstants.METHOD_REPORT, headers));
    }

// Housekeeping

    void performHousekeeping() throws IOException {

        // Create connection if we are active and none exists yet. Note: this can block doing DNS lookups XXX
        if (this.connection == null && this.activeEndpoint != null)
            this.connection = this.msrp.createConnection(this.activeEndpoint);

        // If we don't have a connection for longer than the connect timeout, fail
        if (this.connection == null) {
            final long age = (System.nanoTime() - this.startTime) / 1000000L;
            if (age >= this.msrp.getConnectTimeout())
                throw new IOException("session not bound after " + this.msrp.getConnectTimeout() + "ms");
        }

        // Add the next chunk of each outstanding message to the output queue in round-robin fashion
        if (this.outputQueue.isEmpty()) {
            for (OutputChunks chunks : this.outputChunks.values()) {
                if (chunks.hasNext()) {
                    final MsrpMessage request = chunks.next();
                    this.outputQueue.add(request);
                    this.outputTransactions.put(request.getTransactionId(),
                      new OutputTransaction(chunks, request.getTransactionId()));
                }
            }
        }

        // If we have a connection, move enqueued chunks from my output queue to connection's output queue
        this.flushOutputQueue();

        // Scrub output messages that are complete and either have already been reported on or have timed out waiting
        for (Iterator<OutputChunks> i = this.outputChunks.values().iterator(); i.hasNext(); ) {
            final OutputChunks chunks = i.next();
            if (chunks.hasNext())
                continue;
            if (chunks.getReportListener() == null || chunks.getIdleTime() > MAX_MESSAGE_IDLE_TIME_MILLIS)
                i.remove();
        }

        // Scrub orphaned input messages
        for (Iterator<InputChunks> i = this.inputChunks.values().iterator(); i.hasNext(); ) {
            final InputChunks chunks = i.next();
            assert !chunks.isComplete() && !chunks.isAborted();
            if (chunks.getIdleTime() > MAX_MESSAGE_IDLE_TIME_MILLIS) {
                if (!FailureReport.NO.equals(chunks.getFailureReport())) {
                    this.enqueueReport(chunks.getFromPath(), chunks.getMessageId(),
                      new Status(MsrpConstants.RESPONSE_CODE_TIMEOUT, "Missing message chunks never arrived"), null);
                }
                i.remove();
                continue;
            }
        }

        // Scrub transactions that have timed out waiting for transaction responses
        for (Iterator<OutputTransaction> i = this.outputTransactions.values().iterator(); i.hasNext(); ) {
            final OutputTransaction outputTransaction = i.next();
            if (outputTransaction.getAge() >= MAX_TRANSACTION_AGE_MILLIS) {
                outputTransaction.getOutputChunks().notifyFailure(this, this.callbackExecutor,
                  new Status(MsrpConstants.RESPONSE_CODE_TIMEOUT, "No response rec'd for transaction"));
                i.remove();
            }
        }
    }

    // If we have a connection, move enqueued chunks from my output queue to connection's output queue
    private void flushOutputQueue() throws IOException {
        if (this.connection != null) {
            for (MsrpMessage message; (message = this.outputQueue.pollFirst()) != null; )
                this.connection.write(message);
        }
    }

// OutputTransaction

    /**
     * This object is needed so that when a failure report is requested we can keep track of who to notify.
     */
    private class OutputTransaction {

        private final OutputChunks chunks;
        private final String transactionId;
        private final long sendTime;

        OutputTransaction(OutputChunks chunks, String transactionId) {
            this.chunks = chunks;
            this.transactionId = transactionId;
            this.sendTime = System.nanoTime();
        }

        public OutputChunks getOutputChunks() {
            return this.chunks;
        }

        public String getTransactionId() {
            return this.transactionId;
        }

        public long getAge() {
            return (System.nanoTime() - this.sendTime) / 1000000L;
        }
    }
}

