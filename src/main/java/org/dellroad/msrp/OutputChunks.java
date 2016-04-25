
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

import org.dellroad.msrp.msg.ByteRange;
import org.dellroad.msrp.msg.FailureReport;
import org.dellroad.msrp.msg.Header;
import org.dellroad.msrp.msg.MsrpHeaders;
import org.dellroad.msrp.msg.MsrpMessage;
import org.dellroad.msrp.msg.MsrpRequest;
import org.dellroad.msrp.msg.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents one outgoing MSRP message broken into multiple {@code SEND} {@link MsrpRequest} chunks.
 */
public class OutputChunks implements Closeable, Iterator<MsrpRequest> {

    private static final int MAX_REQUEST_BODY_SIZE = 2048;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final MsrpUri localURI;
    private final MsrpUri remoteURI;
    private final String messageId = MsrpMessage.randomId();
    private final Iterable<? extends Header> headers;
    private final InputStream input;
    private final String contentType;
    private final long size;

    private ReportListener reportListener;
    private long offset;
    private boolean complete;
    private boolean aborted;
    private boolean closed;
    private long timestamp;

    /**
     * Constructor for a message with no body.
     */
    public OutputChunks(MsrpUri localURI, MsrpUri remoteURI, Iterable<? extends Header> headers, ReportListener reportListener) {
        this(localURI, remoteURI, null, -1, null, headers, reportListener);
    }

    /**
     * Constructor for a message with a body.
     *
     * @param localURI local MSRP URI
     * @param remoteURI remote MSRP URI
     * @param input message content input, or null for no content
     * @param size size of input, or -1 if unknown; if not -1 and {@code input} is longer than {@code size}, it will be truncated
     * @param contentType content type
     * @param headers other headers (MIME and/or extension), or null for none
     * @param reportListener success and/or failure listener, or null for none
     * @throws IllegalArgumentException if {@code localURI} or {@code remoteURI} is null
     * @throws IllegalArgumentException if {@code input} is null and {@code size != -1} or {@code contentType} is not null
     * @throws IllegalArgumentException if {@code input} is not null and {@code contentType} is null
     */
    public OutputChunks(MsrpUri localURI, MsrpUri remoteURI, InputStream input, long size,
      String contentType, Iterable<? extends Header> headers, ReportListener reportListener) {
        if (localURI == null)
            throw new IllegalArgumentException("null localURI");
        if (remoteURI == null)
            throw new IllegalArgumentException("null remoteURI");
        if (input == null) {
            if (size != -1)
                throw new IllegalArgumentException("null input requires size = -1");
            if (contentType != null)
                throw new IllegalArgumentException("null input requires null contentType");
        } else {
            if (size < -1)
                throw new IllegalArgumentException("invalid size " + size);
            if (contentType == null)
                throw new IllegalArgumentException("null contentType");
        }
        this.localURI = localURI;
        this.remoteURI = remoteURI;
        this.input = input;
        this.size = size;
        this.contentType = contentType;
        this.reportListener = reportListener;
        this.headers = headers;
        this.timestamp = System.nanoTime();
    }

    /**
     * Get the unique message ID for this message.
     */
    public String getMessageId() {
        return this.messageId;
    }

    /**
     * Get the associated {@link ReportListener}, if any.
     */
    public synchronized ReportListener getReportListener() {
        return this.reportListener;
    }

    /**
     * Get the size of the message body, if known.
     *
     * @return message body size, or -1 if size was not specified and message has not been fully sent yet
     */
    public synchronized long getSize() {
        return this.size == -1 && this.complete ? this.offset : this.size;
    }

    /**
     * Determine whether this instance has been aborted.
     */
    public synchronized boolean isAborted() {
        return this.aborted;
    }

    public synchronized long getIdleTime() {
        return (System.nanoTime() - this.timestamp) / 1000000L;
    }

    /**
     * Notify about success, if appropriate.
     *
     * @param session session on which the message was transmitted
     * @param executor executor used to issue notification
     * @param byteRange range of bytes successfully received
     */
    public synchronized void notifySuccess(final Session session, Executor executor, final ByteRange byteRange) {

        // Sanity check
        if (executor == null)
            throw new IllegalArgumentException("null executor");
        if (byteRange == null)
            throw new IllegalArgumentException("null byteRange");

        // Is success notification desired?
        if (!(this.reportListener instanceof SuccessListener))
            return;
        final SuccessListener successListener = (SuccessListener)this.reportListener;

        // Notify
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    successListener.reportSuccess(session, OutputChunks.this.messageId, byteRange);
                } catch (ThreadDeath t) {
                    throw t;
                } catch (Throwable t) {
                    OutputChunks.this.log.error("error in listener notification", t);
                }
            }
        });
    }

    /**
     * Notify about failure, if appropriate. This method is idempotent.
     *
     * @param session session on which the message was transmitted
     * @param executor executor used to issue notification
     * @param status failure status
     */
    public synchronized void notifyFailure(final Session session, Executor executor, final Status status) {

        // Sanity check
        if (executor == null)
            throw new IllegalArgumentException("null executor");
        if (status == null)
            throw new IllegalArgumentException("null status");

        // Is failure notification desired?
        if (!(this.reportListener instanceof FailureListener))
            return;
        final FailureListener failureListener = (FailureListener)this.reportListener;

        // Only notify once
        this.reportListener = null;

        // Notify
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    failureListener.reportFailure(session, OutputChunks.this.messageId, status);
                } catch (ThreadDeath t) {
                    throw t;
                } catch (Throwable t) {
                    OutputChunks.this.log.error("error in listener notification", t);
                }
            }
        });
    }

// Iterator

    @Override
    public synchronized boolean hasNext() {
        return !this.complete;
    }

    @Override
    public synchronized MsrpRequest next() {

        // Already done?
        if (this.complete)
            throw new NoSuchElementException();

        // Update timestamp
        this.timestamp = System.nanoTime();

        // Generate new transaction ID
        final String transactionId = MsrpMessage.randomId();

        // Prepare MSRP request (without body yet)
        final MsrpHeaders msrpHeaders = new MsrpHeaders();
        msrpHeaders.getFromPath().add(this.localURI);
        msrpHeaders.getToPath().add(this.remoteURI);
        msrpHeaders.setMessageId(this.messageId);
        msrpHeaders.setContentType(this.contentType);
        msrpHeaders.setSuccessReport(false);
        msrpHeaders.setSuccessReport(this.reportListener instanceof SuccessListener);
        if (this.reportListener instanceof FailureListener)
            msrpHeaders.setFailureReport(FailureReport.YES);
        else
            msrpHeaders.setFailureReport(FailureReport.PARTIAL);            // we always want to see 481 or 586 errors
        if (this.headers != null) {
            for (Header header : this.headers) {
                if (MsrpRequest.isMimeHeader(header.getName()))
                    msrpHeaders.getMimeHeaders().add(header);
                else
                    msrpHeaders.getExtensionHeaders().add(header);
            }
        }
        final MsrpRequest request = new MsrpRequest(transactionId, MsrpConstants.METHOD_SEND, msrpHeaders);

        // Aborted?
        if (this.aborted) {
            if (this.input != null)
                msrpHeaders.setByteRange(new ByteRange(this.offset + 1, this.offset, this.size));
            request.setAborted(true);
            this.complete = true;
            return request;
        }

        // No input?
        if (this.input == null) {
            this.complete = true;
            this.closed = true;
            request.setComplete(true);
            return request;
        }

        // Read next chunk of message content and append as body
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final long startingOffset = this.offset;
        try {

            // Read until buffer is full, we've read full amount, or there's no more data to read
            while (body.size() < MAX_REQUEST_BODY_SIZE) {

                // Have we read the full amount?
                if (this.size != -1 && this.offset >= this.size) {
                    this.complete = true;
                    break;
                }

                // Read more data
                final byte[] buf = new byte[MAX_REQUEST_BODY_SIZE - body.size()];
                final int r = this.input.read(buf);

                // No more data?
                if (r < 0) {
                    if (this.size != -1)
                        throw new IOException("expected to read " + this.size + " bytes but only read " + this.offset);
                    this.complete = true;
                    break;
                }

                // Update with new data
                body.write(buf, 0, r);
                this.offset += r;
            }
        } catch (IOException e) {
            this.log.error("I/O error reading MRSP message input, aborting message " + this.messageId, e);
            this.aborted = true;
        }
        request.setBody(body.toByteArray());

        // Set byte range and request flags
        msrpHeaders.setByteRange(new ByteRange(startingOffset + 1, this.offset, this.size));
        request.setComplete(this.complete);
        request.setAborted(this.aborted);

        // Ensure input gets closed
        if (this.aborted || this.complete)
            this.close();

        // Done
        return request;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

// Closeable

    /**
     * Close the {@link InputStream} associated with this instance.
     * This method is idempotent.
     *
     * <p>
     * This class ensures that this method gets invoked after the final {@link MsrpMessage} is
     * retrieved from {@link #next}.
     * </p>
     *
     * <p>
     * If the message has not been fully sent when this method is invoked, the message will be aborted.
     * </p>
     */
    @Override
    public synchronized void close() {

        // Sanity check
        if (this.closed)
            return;
        this.closed = true;

        // If not completed, abort
        if (!this.complete)
            this.aborted = true;

        // Close input
        try {
            if (this.input != null)
                this.input.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

