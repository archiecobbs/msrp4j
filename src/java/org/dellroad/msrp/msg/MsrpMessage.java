
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.dellroad.msrp.MsrpConstants;

/**
 * MSRP request/response support superclass.
 */
public abstract class MsrpMessage {

    private final String transactionId;
    private final MsrpHeaders headers;

    /**
     * Default constructor. Generates a random transaction ID.
     */
    protected MsrpMessage() {
        this(null, null);
    }

    /**
     * Construct an instance with the given transaction ID and headers.
     *
     * @param transactionId transaction ID, or null to have a randomly generated one assigned
     * @param headers MSRP headers, or null to have an empty instance created
     * @throws IllegalArgumentException if {@code transactionId} is invalid
     */
    protected MsrpMessage(String transactionId, MsrpHeaders headers) {
        this.transactionId = transactionId != null ? transactionId : MsrpMessage.randomId();
        if (!Pattern.compile(Util.IDENT_REGEX).matcher(this.transactionId).matches())
            throw new IllegalArgumentException("invalid transaction ID `" + this.transactionId + "'");
        this.headers = headers != null ? headers : new MsrpHeaders();
    }

    /**
     * Get the transaction ID associated with this instance.
     */
    public String getTransactionId() {
        return this.transactionId;
    }

    /**
     * Get the headers associated with this instance.
     */
    public MsrpHeaders getHeaders() {
        return this.headers;
    }

    /**
     * Encode this instance according to RFC 4975.
     *
     * @param withBody true to include the body, or false to omit the body, if any
     */
    public byte[] encode(boolean withBody) {
        try {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            final OutputStreamWriter bufWriter = new OutputStreamWriter(buf, Util.UTF8);
            bufWriter.write(this.getFirstLine());
            bufWriter.write(Util.CRLF);
            bufWriter.write(this.headers.toString());
            bufWriter.flush();
            if (withBody)
                this.writePayload(buf);
            bufWriter.write(MsrpConstants.END_LINE_PREFIX);
            bufWriter.write(this.transactionId);
            bufWriter.write(this.getFlagByte() & 0xff);
            bufWriter.write(Util.CRLF);
            bufWriter.flush();
            return buf.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("unexpected exception");
        }
    }

    /**
     * Generate a random identifier for use as transaction or message ID.
     */
    public static String randomId() {
        return String.format("%016x", ThreadLocalRandom.current().nextLong());
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final MsrpMessage that = (MsrpMessage)obj;
        if (!(this.transactionId != null ? this.transactionId.equals(that.transactionId) : that.transactionId == null))
            return false;
        if (!(this.headers != null ? this.headers.equals(that.headers) : that.headers == null))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return (this.transactionId != null ? this.transactionId.hashCode() : 0)
          ^ (this.headers != null ? this.headers.hashCode() : 0);
    }

    @Override
    public String toString() {
        return new String(this.encode(false), Util.UTF8);
    }

// Subclass methods

    /**
     * Get the end line flag byte.
     */
    protected abstract byte getFlagByte();

    /**
     * Get the first message line.
     */
    protected abstract String getFirstLine();

    /**
     * Write the message body, if any.
     *
     * <p>
     * If this message has a body, this method should output CRLF, followed by the binary body content, followed by a final CRLF.
     * If this message has no body, this method should not output anything.
     * <p>
     */
    protected abstract void writePayload(OutputStream output) throws IOException;
}

