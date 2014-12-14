
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;

import org.dellroad.msrp.msg.MsrpInputParser;
import org.dellroad.msrp.msg.MsrpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Connection {

    private static final int BUFFER_SIZE = 1460;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Msrp msrp;
    private final Endpoint endpoint;
    private final SocketChannel socketChannel;
    private final SelectionKey selectionKey;
    private final ArrayDeque<ByteBuffer> outputQueue = new ArrayDeque<>();
    private final MsrpInputParser parser = new MsrpInputParser();

    private long lastActiveTime;
    private boolean closed;

    /**
     * Connecting constructor.
     *
     * @param endpoint remote endpoint
     * @throws IllegalArgumentException if {@code endpoint} is null
     */
    public Connection(Msrp msrp, Endpoint endpoint, SocketChannel socketChannel) throws IOException {
        if (msrp == null)
            throw new IllegalArgumentException("null msrp");
        if (endpoint == null)
            throw new IllegalArgumentException("null endpoint");
        if (socketChannel == null)
            throw new IllegalArgumentException("null socketChannel");
        this.msrp = msrp;
        this.endpoint = endpoint;
        this.socketChannel = socketChannel;
        this.lastActiveTime = System.nanoTime();
        this.selectionKey = this.msrp.createSelectionKey(this.socketChannel, new SelectorService() {
            @Override
            public void serviceIO(SelectionKey key) throws IOException {
                if (key.isConnectable())
                    Connection.this.handleConnectable();
                if (key.isReadable())
                    Connection.this.handleReadable();
                if (key.isWritable())
                    Connection.this.handleWritable();
            }
            @Override
            public void close(Exception cause) {
                Connection.this.close(cause);
            }
        });

        // Set up selection
        if (this.socketChannel.isConnectionPending())
            this.selectFor(SelectionKey.OP_CONNECT, true);
        else
            this.selectFor(SelectionKey.OP_READ, true);
    }

    /**
     * Get remote {@link Endpoint}.
     */
    public Endpoint getEndpoint() {
        return this.endpoint;
    }

    /**
     * Get time in milliseconds since last activity.
     */
    public long getIdleTime() {
        return (System.nanoTime() - this.lastActiveTime) / 1000000L;
    }

    /**
     * Enqueue a message on this connection.
     */
    public void write(MsrpMessage message) throws IOException {
        if (message == null)
            throw new IllegalArgumentException("null message");
        this.outputQueue.add(ByteBuffer.wrap(message.encode(true)));
        if (this.socketChannel.isConnected())
            this.selectFor(SelectionKey.OP_WRITE, true);
        this.lastActiveTime = System.nanoTime();
        this.msrp.wakeup();
    }

    /**
     * Close this connection.
     *
     * @param cause error that occurred, if any, otherwise null
     * @return true if this instance was closed, false if this instance was already closed
     */
    public boolean close(Exception cause) {
        if (this.closed)
            return false;
        this.closed = true;
        if (this.log.isDebugEnabled())
            this.log.debug("closing " + this + ", cause: " + cause);
        try {
            this.socketChannel.close();
        } catch (IOException e) {
            // ignore
        }
        this.msrp.handleConnectionClosed(this, cause);
        return true;
    }

    @Override
    public String toString() {
        return "Connection[endpoint=" + this.endpoint + ",closed=" + this.closed + "]";
    }

// Service

    private void handleConnectable() throws IOException {
        this.selectFor(SelectionKey.OP_CONNECT, false);
        if (!this.socketChannel.finishConnect())                    // this should never occur
            throw new IOException("connection failed");
        if (this.log.isDebugEnabled())
            this.log.debug(this + ": connection succeeded");
        this.selectFor(SelectionKey.OP_READ, true);
        this.selectFor(SelectionKey.OP_WRITE, !this.outputQueue.isEmpty());
        this.lastActiveTime = System.nanoTime();
    }

    private void handleReadable() throws IOException {
        while (true) {

            // Update timestamp
            this.lastActiveTime = System.nanoTime();

            // Read bytes
            final ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
            final long len = this.socketChannel.read(buf);
            if (len == -1)
                throw new EOFException("connection closed");

            // Parse bytes and handle message(s)
            for (int i = 0; i < len; i++) {
                final MsrpMessage message = this.parser.inputMessageByte(buf.get(i));
                if (message != null)
                    this.msrp.handleMessage(this, message);
            }

            // Done reading?
            if (len < BUFFER_SIZE)
                return;
        }
    }

    private void handleWritable() throws IOException {
        final ByteBuffer buf = this.outputQueue.peekFirst();
        if (buf != null) {
            this.socketChannel.write(buf);
            if (!buf.hasRemaining())
                this.outputQueue.removeFirst();
            this.lastActiveTime = System.nanoTime();
        }
        this.selectFor(SelectionKey.OP_WRITE, !this.outputQueue.isEmpty());
    }

    void performHousekeeping(boolean active) throws IOException {
        if (this.socketChannel.isConnectionPending()) {
            if (this.getIdleTime() >= this.msrp.getConnectTimeout())
                throw new IOException("connection unsuccessful after " + this.getIdleTime() + "ms");
        } else {
            if (!active && this.getIdleTime() >= this.msrp.getMaxIdleTime())
                throw new IOException("connection idle timeout after " + this.getIdleTime() + "ms");
        }
    }

// Helpers

    private void selectFor(int ops, boolean enabled) throws IOException {
        if (this.selectionKey != null) {
            final int currentOps = this.selectionKey.interestOps();
            this.selectionKey.interestOps(enabled ? currentOps | ops : currentOps & ~ops);
        }
    }
}

