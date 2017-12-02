
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.io.IOException;
import java.io.OutputStream;

import org.dellroad.msrp.MsrpConstants;

/**
 * MSRP response.
 */
public class MsrpResponse extends MsrpMessage {

    private final int code;
    private final String comment;

    /**
     * Construct an instance with the given transaction ID, code, and comment.
     *
     * @param transactionId transaction ID, or null to have a randomly generated one assigned
     * @param code response code
     * @param comment response code comment, if any, otherwise null
     * @throws IllegalArgumentException if {@code code} is not in the range 000 .. 999
     * @throws IllegalArgumentException if {@code transactionId} is invalid
     */
    public MsrpResponse(String transactionId, int code, String comment) {
        this(transactionId, code, comment, null);
    }

    /**
     * Construct an instance with the given transaction ID, code, comment, and headers.
     *
     * @param transactionId transaction ID, or null to have a randomly generated one assigned
     * @param code response code
     * @param comment response code comment, if any, otherwise null
     * @param headers MSRP headers, or null to have an empty instance created
     * @throws IllegalArgumentException if {@code code} is not in the range 000 .. 999
     * @throws IllegalArgumentException if {@code transactionId} is invalid
     */
    public MsrpResponse(String transactionId, int code, String comment, MsrpHeaders headers) {
        super(transactionId, headers);
        if (code < 0 || code > 999)
            throw new IllegalArgumentException("invalid code " + code);
        this.code = code;
        this.comment = comment;
    }

    public int getCode() {
        return this.code;
    }

    public String getComment() {
        return this.comment;
    }

    /**
     * Get the result string (starting with the response code) from the first line of this response.
     *
     * @return result string
     */
    public String getResultString() {
        return String.format("%03d%s", this.code, this.comment != null ? " " + this.comment : "");
    }

    /**
     * Get this response's response code and comment as a {@link Status} object.
     *
     * @return associated {@link Status}
     */
    public Status toStatus() {
        return new Status(this.code, this.comment);
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!super.equals(obj))
            return false;
        final MsrpResponse that = (MsrpResponse)obj;
        if (this.code != that.code)
            return false;
        if (!(this.comment != null ? this.comment.equals(that.comment) : that.comment == null))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.code ^ (this.comment != null ? this.comment.hashCode() : 0);
    }

// Subclass overrides

    @Override
    public byte getFlagByte() {
        return MsrpConstants.FLAG_COMPLETE;
    }

    @Override
    protected String getFirstLine() {
        return "MSRP " + this.getTransactionId() + " " + this.getResultString();
    }

    @Override
    protected void writePayload(OutputStream output) throws IOException {
        // responses never have a payload so there's nothing to do
    }
}

