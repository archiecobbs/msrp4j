
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.dellroad.msrp.MsrpConstants;

/**
 * MSRP request.
 */
public class MsrpRequest extends MsrpMessage {

    private final String method;

    private byte[] body;
    private boolean complete = true;
    private boolean aborted;

    /**
     * Construct an instance with no body and a random transaction ID.
     *
     * @param method request method
     */
    public MsrpRequest(String method) {
        this(null, method, null);
    }

    /**
     * Construct an instance with no body.
     *
     * @param transactionId transaction ID, or null to have a randomly generated one assigned
     * @param method request method
     * @param headers MSRP headers, or null to have an empty instance created
     * @throws IllegalArgumentException if {@code method} is null
     * @throws IllegalArgumentException if {@code transactionId} is invalid
     */
    public MsrpRequest(String transactionId, String method, MsrpHeaders headers) {
        this(transactionId, method, headers, null);
    }

    /**
     * Construct an instance with a body.
     *
     * @param transactionId transaction ID
     * @param method request method
     * @param headers MSRP headers, or null to have an empty instance created
     * @param body payload body, or null for none
     * @throws IllegalArgumentException if {@code method} is null or invalid
     * @throws IllegalArgumentException if {@code transactionId} is invalid
     */
    public MsrpRequest(String transactionId, String method, MsrpHeaders headers, byte[] body) {
        super(transactionId, headers);
        if (method == null)
            throw new IllegalArgumentException("null method");
        if (!Pattern.compile(Util.METHOD_REGEX).matcher(method).matches())
            throw new IllegalArgumentException("invalid method `" + method + "'");
        this.method = method;
        this.body = body;
    }

    /**
     * Get the MSRP request method associated with this instance.
     */
    public String getMethod() {
        return this.method;
    }

    /**
     * Get the body payload associated with this instance, if any.
     *
     * @return body, or null if there is none
     */
    public byte[] getBody() {
        return this.body;
    }
    public void setBody(byte[] body) {
        this.body = body;
    }

    /**
     * Get the whether this message body was flagged as complete.
     * Default is true.
     */
    public boolean isComplete() {
        return this.complete;
    }
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /**
     * Get the whether this message body was flagged as aborted.
     * Default is false.
     */
    public boolean isAborted() {
        return this.aborted;
    }
    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    /**
     * Build an {@link MsrpResponse} to this request, if appropriate, with the given error code and comment.
     *
     * @param code three digit error code
     * @param comment error comment, or null
     * @return valid response, or null if this request should not generate any response (i.e., {@link FailureReport#NO}).
     * @throws IllegalArgumentException if {@code code} is not in the range 000 .. 999
     */
    public MsrpResponse buildResponse(int code, String comment) {
        if (FailureReport.NO.equals(this.getHeaders().getFailureReport()))
            return null;
        final MsrpHeaders responseHeaders = new MsrpHeaders();
        if (this.getMethod().equals(MsrpConstants.METHOD_SEND))
            responseHeaders.getToPath().add(this.getHeaders().getFromPath().get(0));
        else
            responseHeaders.getToPath().addAll(this.getHeaders().getFromPath());
        responseHeaders.getFromPath().add(this.getHeaders().getToPath().get(0));
        return new MsrpResponse(this.getTransactionId(), code, comment, responseHeaders);
    }

    /**
     * Determine if the given header name is a MIME header.
     *
     * @throws IllegalArgumentException if {@code name} is null
     */
    public static boolean isMimeHeader(String name) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        final String prefix = MsrpConstants.MIME_CONTENT_HEADER_PREFIX;
        return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!super.equals(obj))
            return false;
        final MsrpRequest that = (MsrpRequest)obj;
        if (!this.method.equals(that.method))
            return false;
        if (!(this.body != null ? that.body != null && Arrays.equals(this.body, that.body) : that.body == null))
            return false;
        if (this.complete != that.complete)
            return false;
        if (this.aborted != that.aborted)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode()
          ^ this.method.hashCode()
          ^ (this.body != null ? Arrays.hashCode(this.body) : 0)
          ^ (this.complete ? 1 : 0)
          ^ (this.aborted ? 2 : 0);
    }

// Subclass overrides

    @Override
    public byte getFlagByte() {
        return this.aborted ? MsrpConstants.FLAG_ABORT :
          this.complete ? MsrpConstants.FLAG_COMPLETE : MsrpConstants.FLAG_INCOMPLETE;
    }

    @Override
    protected String getFirstLine() {
        return "MSRP " + this.getTransactionId() + " " + this.method;
    }

    @Override
    protected void writePayload(OutputStream output) throws IOException {
        if (this.body != null) {
            output.write('\r');
            output.write('\n');
            output.write(this.body);
            output.write('\r');
            output.write('\n');
        }
    }
}

