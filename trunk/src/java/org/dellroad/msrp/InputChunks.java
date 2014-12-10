
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dellroad.msrp.msg.ByteRange;
import org.dellroad.msrp.msg.FailureReport;
import org.dellroad.msrp.msg.Header;
import org.dellroad.msrp.msg.MsrpHeaders;
import org.dellroad.msrp.msg.MsrpRequest;
import org.dellroad.msrp.msg.ProtocolException;

/**
 * Represents one incoming MSRP message reassembled from multiple {@code SEND} {@link MsrpRequest} chunks.
 */
public class InputChunks {

    private final String messageId;
    private final long maxContentLength;
    private final TreeSet<Header> headers = new TreeSet<Header>(Header.SORT_BY_NAME);
    private final ArrayList<long[]> holes = new ArrayList<>();

    private List<MsrpUri> fromPath;
    private boolean successReport;
    private FailureReport failureReport = FailureReport.YES;
    private String contentType;
    private boolean complete;
    private boolean aborted;
    private long contentLength = -1;
    private long timestamp;
    private byte[] buf;

    /**
     * Constructor.
     *
     * @param messageId incoming message ID
     * @param maxContentLength maximum allowed message content length
     * @throws IllegalArgumentException if {@code messageId} is null
     */
    public InputChunks(String messageId, long maxContentLength) {
        if (messageId == null)
            throw new IllegalArgumentException("null messageId");
        this.messageId = messageId;
        this.maxContentLength = maxContentLength;
        this.timestamp = System.nanoTime();
    }

    /**
     * Handle an incoming SEND request for this message. Also resets this instance's idle timer.
     *
     * @param request incoming SEND request associated with this message
     * @return true if this message is now complete (or aborted); false if more chunks are still required
     * @throws IllegalArgumentException if {@code request} is null
     * @throws IllegalArgumentException if {@code request} is not a {@code SEND} request
     * @throws IllegalArgumentException if {@code request} has the wrong message ID
     * @throws ProtocolException if a protocol violation is detected
     */
    public synchronized boolean handleSend(MsrpRequest request) throws ProtocolException {

        // Sanity check
        if (request == null)
            throw new IllegalArgumentException("null request");
        if (!MsrpConstants.METHOD_SEND.equals(request.getMethod()))
            throw new IllegalArgumentException("request method " + request.getMethod() + " != " + MsrpConstants.METHOD_SEND);
        final MsrpHeaders requestHeaders = request.getHeaders();
        if (!this.messageId.equals(requestHeaders.getMessageId()))
            throw new IllegalArgumentException("request message ID " + requestHeaders.getMessageId() + " != " + this.messageId);

        // Update timestamp
        this.timestamp = System.nanoTime();

        // Get From-Path and success/failure report info
        this.fromPath = requestHeaders.getFromPath();
        this.successReport |= requestHeaders.isSuccessReport();
        if (requestHeaders.getFailureReport() != null)
            this.failureReport = requestHeaders.getFailureReport();

        // Get extension header info
        this.headers.addAll(requestHeaders.getExtensionHeaders());

        // Handle aborted message
        if (request.isAborted()) {
            this.aborted = true;
            return true;
        }

        // Get body; if none, check sanity
        final byte[] body = request.getBody();
        if (body == null) {
            if (this.buf != null)
                throw new ProtocolException("continuation request must have a body");
            final ByteRange byteRange = requestHeaders.getByteRange();
            if (byteRange != null && !byteRange.equals(ByteRange.EMPTY))
                throw new ProtocolException("invalid ByteRange " + byteRange + " for message having no body");
            this.complete = true;
            return true;
        }

        // Get MIME header info
        this.contentType = requestHeaders.getContentType();
        this.headers.addAll(requestHeaders.getMimeHeaders());

        // Get/infer byte range and validate
        ByteRange byteRange = requestHeaders.getByteRange();
        if (byteRange == null)
            byteRange = new ByteRange(body.length);
        final long expectedEnd = byteRange.getStart() + body.length - 1;
        if (byteRange.getEnd() == -1)
            byteRange = new ByteRange(byteRange.getStart(), expectedEnd, byteRange.getTotal());
        else if (byteRange.getEnd() != expectedEnd) {
            throw new ProtocolException("rec'd inconsistent ByteRange " + byteRange + " with end byte "
              + byteRange.getEnd() + " != " + expectedEnd + " expected based on body size");
        }

        // Determine the range of content bytes we just received
        final long offset = byteRange.getStart() - 1;
        final long limit = offset + body.length;

        // Infer/validate content length
        if (this.contentLength == -1) {
            if (byteRange.getTotal() != -1)
                this.contentLength = byteRange.getTotal();
        } else if (byteRange.getTotal() != -1 && byteRange.getTotal() != this.contentLength) {
            throw new ProtocolException("rec'd inconsistent ByteRange " + byteRange
              + " with total " + byteRange.getTotal() + " != previously rec'd total " + this.contentLength);
        }

        // Verify final chunk is not marked incomplete
        if (this.contentLength != -1 && byteRange.getEnd() == this.contentLength && !request.isComplete())
            throw new ProtocolException("last chunk in " + byteRange + " message has unexpected incomplete flag");

        // Check content length not too big
        final long minimumLength = Math.max(limit, this.contentLength);
        if (minimumLength > this.maxContentLength || minimumLength > Integer.MAX_VALUE)     // TODO: allow long length with stream
            throw new ProtocolException("content is too large (" + minimumLength + " > " + this.maxContentLength + " bytes)");

        // Create content buffer if it doesn't already exist
        if (this.buf == null)
            this.buf = new byte[0];

        // Expand content buffer as needed and add a corresponding hole TODO: allow streaming to a file instead of into memory
        if (this.buf.length < minimumLength) {
            this.holes.add(new long[] { this.buf.length, minimumLength });
            final byte[] newBuf = new byte[(int)minimumLength];
            System.arraycopy(this.buf, 0, newBuf, 0, this.buf.length);
            this.buf = newBuf;
        }

        // Merge new data into content buffer and update holes
        System.arraycopy(body, 0, this.buf, (int)offset, body.length);
        for (int i = 0; i < this.holes.size(); i++) {
            final long[] hole = this.holes.get(i);
            assert hole[0] < hole[1];
            if (hole[1] <= offset || hole[0] >= limit)      // entirely before or after body (i.e., not a factor)
                continue;
            if (hole[0] >= offset && hole[1] <= limit)      // entirely contained within body
                this.holes.remove(i--);
            else if (hole[1] <= limit)                      // straddles body's left border
                hole[1] = offset;
            else if (hole[0] >= offset)                     // straddles body's right border
                hole[0] = limit;
            else {                                          // entirely contains body => split it in two
                this.holes.set(i, new long[] { hole[0], offset });
                this.holes.add(++i, new long[] { limit, hole[1] });
            }
        }

        // Are we complete now?
        this.complete |= request.isComplete() && this.holes.isEmpty();

        // Done
        return this.complete;
    }

    /**
     * Get idle time of this instance.
     *
     * @return time in milliseconds since (construction or) the most recent invocation of {@link #handleSend handleSend()}
     */
    public synchronized long getIdleTime() {
        return (System.nanoTime() - this.timestamp) / 1000000L;
    }

    /**
     * Get message ID.
     */
    public synchronized String getMessageId() {
        return this.messageId;
    }

    /**
     * Get From path.
     */
    public synchronized List<MsrpUri> getFromPath() {
        return this.fromPath;
    }

    /**
     * Get message content.
     *
     * @return message content, or null if this message does not contain any content
     */
    public synchronized byte[] getContent() {
        return this.buf;
    }

    /**
     * Get message content type.
     *
     * @return message content type, or null if this message does not contain any content
     */
    public synchronized String getContentType() {
        return this.contentType;
    }

    /**
     * Get other headers, sorted by header name case-insensitively.
     */
    public synchronized SortedSet<Header> getHeaders() {
        return Collections.<Header>unmodifiableSortedSet(this.headers);
    }

    /**
     * Determine whether this instance is complete.
     */
    public synchronized boolean isComplete() {
        return this.complete;
    }

    /**
     * Determine whether this instance was aborted.
     */
    public synchronized boolean isAborted() {
        return this.aborted;
    }

    /**
     * Determine whether this message requires a success report.
     */
    public synchronized boolean isSuccessReport() {
        return this.successReport;
    }

    /**
     * Determine what type of failure reporting this message requires.
     */
    public synchronized FailureReport getFailureReport() {
        return this.failureReport;
    }
}

