
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayOutputStream;

/**
 * Parses CRLF-terminated lines.
 */
public class LineInputParser {

    private final int maxLength;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    private boolean pendingCR;

    /**
     * Constructor.
     *
     * @param maxLength maximum allowed line length in bytes
     * @throws IllegalArgumentException if {@code maxLength} negative
     */
    public LineInputParser(int maxLength) {
        if (maxLength < 0)
            throw new IllegalArgumentException("maxLength < 0");
        this.maxLength = maxLength;
    }

    /**
     * Input the next byte.
     *
     * @param b input byte
     * @return complete line (not including CRLF), or null if more bytes are needed
     * @throws ProtocolException if the line exceeds the maximum allowed length
     */
    public String inputLineByte(byte b) throws ProtocolException {

        // Update state machine
        if (this.pendingCR) {
            if (b == '\n') {
                final String line = new String(this.buf.toByteArray(), Util.UTF8);
                this.reset();
                return line;
            }
            buf.write('\r');            // flush the pending CR
        }
        this.pendingCR = b == '\r';
        if (!this.pendingCR)
            buf.write(b);

        // Check max length
        if (buf.size() >= this.maxLength) {
            final String prefix = new String(buf.toByteArray(), 0, Util.TRUNCATE_LENGTH, Util.UTF8);
            throw new ProtocolException("message line starting with " + Util.quotrunc(prefix)
              + " is too long (longer than " + this.maxLength + " bytes)");
        }

        // Done
        return null;
    }

    /**
     * Determine whether this instance is sitting at a line boundary.
     *
     * <p>
     * This will be true after initial construction, invocation of {@link #reset},
     * or an invocation {@link #inputLineByte inputLineByte()} that returned a non-null value.
     * </p>
     */
    public boolean isBetweenLines() {
        return this.buf.size() == 0 && !this.pendingCR;
    }

    /**
     * Reset parse state.
     */
    public void reset() {
        this.buf.reset();
        this.pendingCR = false;
    }
}

