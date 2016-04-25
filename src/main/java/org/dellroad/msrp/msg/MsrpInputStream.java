
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps an {@link InputStream} and parses MSRP messages from the underlying input.
 */
public class MsrpInputStream extends FilterInputStream {

    private final MsrpInputParser parser;

    /**
     * Constructor. Uses a {@link MsrpInputParser} with default parameters.
     *
     * @param input underlying input
     * @throws IllegalArgumentException if {@code input} is null
     */
    public MsrpInputStream(InputStream input) {
        this(input, new MsrpInputParser());
    }

    /**
     * Primary constructor.
     *
     * @param input underlying input
     * @param parser parser
     * @throws IllegalArgumentException if either parameter is null
     */
    public MsrpInputStream(InputStream input, MsrpInputParser parser) {
        super(input);
        if (input == null)
            throw new IllegalArgumentException("null input");
        if (parser == null)
            throw new IllegalArgumentException("null parser");
        this.parser = parser;
    }

    /**
     * Get the {@link MsrpInputParser} associated with this instance.
     */
    public MsrpInputParser getParser() {
        return this.parser;
    }

    /**
     * Read the next {@link MsrpMessage} from the underlying input stream.
     *
     * @return next message read, or null if EOF is detected
     * @throws IOException if an I/O error occurs
     * @throws ProtocolException if a protocol violation is detected
     * @throws EOFException if the remote side has closed the connection in the middle of a message
     */
    public MsrpMessage readMsrpMessage() throws IOException {
        while (true) {
            final int b = this.read();
            if (b == -1) {
                if (!this.parser.isBetweenMessages())
                    throw new EOFException("truncated message");
                return null;
            }
            final MsrpMessage result = this.parser.inputMessageByte((byte)b);
            if (result != null)
                return result;
        }
    }
}

