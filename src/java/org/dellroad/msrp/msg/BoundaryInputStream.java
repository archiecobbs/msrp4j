
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads from an underlying {@link InputStream} until an MSRP end-line boundary is encountered.
 */
public class BoundaryInputStream extends FilterInputStream {

    private final BoundaryInputParser parser;
    private byte[] data;
    private int offset;
    private boolean eof;

    /**
     * Constructor.
     *
     * @param input underlying input
     * @param transactionId transaction ID
     * @throws IllegalArgumentException if {@code input} is null
     * @throws IllegalArgumentException if {@code transactionId} is null or invalid
     */
    public BoundaryInputStream(InputStream input, String transactionId) {
        super(input);
        this.parser = new BoundaryInputParser(transactionId);
    }

// InputStream methods

    /**
     * Read the next byte.
     *
     * @return next byte prior to boundary, or -1 if the boundary has been seen
     * @throws EOFException if EOF is detected before the boundary is seen
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException {

        // Done?
        if (this.eof)
            return -1;

        // Shift input bytes into parser until we get something back
        while (this.data == null || this.offset == this.data.length) {
            final int r = super.read();
            if (r == -1)
                throw new EOFException("detected EOF before boundary string was matched");
            if ((this.data = this.parser.inputContentByte((byte)r)) == null) {
                this.eof = true;
                return -1;
            }
            this.offset = 0;
        }

        // Return next byte
        return this.data[this.offset++];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (off < len) {
            final int r = this.read();
            if (r == -1)
                return total == 0 ? -1 : total;
            b[off++] = (byte)r;
            total++;
        }
        return total;
    }

    @Override
    public long skip(long n) throws IOException {
        long total = 0;
        while (n-- > 0) {
            if (this.read() == -1)
                break;
            total++;
        }
        return total;
    }

    @Override
    public int available() throws IOException {
        return this.data != null ? this.data.length - this.offset : 0;
    }

    /**
     * Mark this instance. This operation is not supported by {@link BoundaryInputStream}.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    /**
     * Reset this instance. This operation is not supported by {@link BoundaryInputStream}.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    /**
     * Determine if mark/reset is supported by this instance. Mark/reset is not supported by {@link BoundaryInputStream}.
     *
     * @return false always
     */
    @Override
    public boolean markSupported() {
        return false;
    }

// Other public methods

    /**
     * Get the flag byte that was found in the boundary string.
     *
     * <p>
     * If this instance's {@link #read read()} method has not yet returned -1, then the return
     * value from this method is undefined.
     * </p>
     */
    public byte getFlagByte() {
        return this.parser.getFlagByte();
    }
}

