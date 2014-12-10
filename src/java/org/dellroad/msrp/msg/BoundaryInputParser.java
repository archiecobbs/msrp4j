
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

import org.dellroad.msrp.MsrpConstants;

/**
 * Parses body content bytes separated by MSRP end-line boundaries.
 *
 * <p>
 * Instances are not thread safe.
 * </p>
 */
public class BoundaryInputParser {

    private final byte[] terminator;        // terminator string (with variable flag byte)
    private final int flagOffset;           // offset of flag byte in terminator

    private boolean boundary = true;        // whether we are between body parts
    private int matched;                    // how many bytes of terminator we have matched so far

    /**
     * Constructor.
     *
     * @param transactionId transaction ID
     * @throws IllegalArgumentException if {@code transactionId} is null or invalid
     */
    public BoundaryInputParser(String transactionId) {

        // Sanity check
        if (transactionId == null)
            throw new IllegalArgumentException("null transactionId");
        if (!Pattern.compile(Util.IDENT_REGEX).matcher(transactionId).matches())
            throw new IllegalArgumentException("illegal transactionId");

        // Build byte sequence that ends the body (note, the '?' is a placeholder for the flag byte)
        this.terminator = (Util.CRLF + MsrpConstants.END_LINE_PREFIX + transactionId + "?" + Util.CRLF).getBytes(Util.UTF8);
        this.flagOffset = 2 + MsrpConstants.END_LINE_PREFIX.length() + transactionId.length();
        assert terminator[flagOffset] == (byte)'?';
    }

    /**
     * Input the next byte and get back any new content bytes that have been determined to not be part of the boundary string.
     *
     * @param b input byte
     * @return zero or more additional body bytes, or null if the boundary string has been seen
     * @throws ProtocolException if the body exceeds the maximum allowed length
     */
    public byte[] inputContentByte(byte b) {

        // No longer at boundary
        this.boundary = false;

        // Does this byte match the next terminator prefix byte? Note special case for flag byte.
        if (matched == this.flagOffset ?
          b == MsrpConstants.FLAG_INCOMPLETE || b == MsrpConstants.FLAG_COMPLETE || b == MsrpConstants.FLAG_ABORT :
          b == this.terminator[matched]) {

            // Replace '?' with the actual flag seen when we see it
            if (this.matched == this.flagOffset)
                this.terminator[this.flagOffset] = b;

            // Increment the number of terminator bytes matched; have we now matched the entire terminator?
            if (++this.matched == this.terminator.length) {
                this.reset();
                return null;
            }

            // End line not fully matched yet, and there is no additional data at this point
            return new byte[0];
        }

        // Were any bytes previously matched? If not, this is easy.
        if (this.matched == 0)
            return new byte[] { b };

        // Match failure: roll back to the next longest prefix (this is O(n^2)... better would be Knuth-Morris-Pratt variant)
        final ByteArrayOutputStream buf = new ByteArrayOutputStream(this.terminator.length);
        buf.write(this.terminator[0]);
    retractionLoop:
        for (int offset = 1; this.matched > 0; offset++) {

            // Check whether terminator[offset..end] + b matches a prefix
            for (int i = 0; i < this.matched; i++) {
                final byte next = (i < this.matched - 1) ? terminator[offset + i] : b;
                if (terminator[i] != next) {
                    buf.write(--this.matched > 0 ? this.terminator[offset] : b);
                    continue retractionLoop;
                }
            }
            break;
        }

        // Return available bytes
        return buf.toByteArray();
    }

    /**
     * Determine whether this instance is sitting at a content boundary.
     *
     * <p>
     * This will be true after initial construction, invocation of {@link #reset},
     * or an invocation {@link #inputContentByte inputContentByte()} that returned a null value.
     * </p>
     */
    public boolean isOnBoundary() {
        return this.boundary;
    }

    /**
     * Reset parse state.
     */
    public void reset() {
        this.boundary = true;
        this.matched = 0;
    }

    /**
     * Get the flag byte that was found in the boundary string.
     *
     * <p>
     * If the complete boundary string has not yet been encountered, then the return
     * value from this method is undefined.
     * </p>
     */
    public byte getFlagByte() {
        return this.terminator[this.flagOffset];
    }
}

