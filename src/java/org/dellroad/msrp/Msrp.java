
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import org.dellroad.msrp.msg.MsrpInputParser;
import org.dellroad.msrp.msg.MsrpMessage;
import org.dellroad.msrp.msg.MsrpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An MSRP protocol stack.
 *
 * <p>
 * Instances must be {@link #start}ed before being used. Once started, new MSRP {@link Session}s may be created
 * by invoking {@link #createSession createSession()} with the local and remote URI's, a {@link SessionListener}
 * for receiving notification of session events, and an {@link Executor} by which notifications will be delivered.
 * </p>
 *
 * <p>
 * Invoking {@link #stop} shuts down all active sessions and stops an instance. A stopped instance may then be
 * restarted if desired.
 * </p>
 */
public class Msrp {

    /**
     * Default maximum number of allowed sessions ({@value #DEFAULT_MAX_SESSIONS}).
     *
     * @see #getMaxSessions
     */
    public static final int DEFAULT_MAX_SESSIONS = 1000;

    /**
     * Default idle connection timeout ({@value #DEFAULT_MAX_IDLE_TIME} milliseconds).
     *
     * @see #getMaxIdleTime
     */
    public static final long DEFAULT_MAX_IDLE_TIME = 30 * 1000L;        // 30 sec

    /**
     * Default connect timeout for outgoing connections ({@value #DEFAULT_CONNECT_TIMEOUT} milliseconds).
     *
     * @see #getConnectTimeout
     */
    public static final long DEFAULT_CONNECT_TIMEOUT = 20 * 1000L;      // 20 sec

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final TreeMap<MsrpUri, Session> sessionMap = new TreeMap<>(MsrpUriComparator.INSTANCE);
    private final HashSet<Connection> connections = new HashSet<>();

    private InetSocketAddress listenAddress;
    private int maxSessions = DEFAULT_MAX_SESSIONS;
    private long maxContentLength = MsrpInputParser.DEFAULT_MAX_CONTENT_LENGTH;
    private long maxIdleTime = DEFAULT_MAX_IDLE_TIME;
    private long connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    private ServerSocketChannel serverSocketChannel;
    private SelectionKey selectionKey;
    private Selector selector;
    private ServiceThread serviceThread;

    /**
     * Constructor.
     */
    public Msrp() {
    }

// Public API

    /**
     * Get the {@link InetSocketAddress} to which this instance is bound.
     *
     * @return listen address, possibly null
     */
    public synchronized InetSocketAddress getListenAddress() {
        return this.listenAddress;
    }

    /**
     * Set the {@link InetSocketAddress} to which this instance should bind.
     *
     * <p>
     * If this instance is already started, invoking this method will have no effect until it is {@linkplain #stop stopped}
     * and restarted.
     * </p>
     *
     * <p>
     * By default, instances listen on all interfaces on port {@link MsrpConstants#DEFAULT_PORT}.
     * </p>
     *
     * @param listenAddress listen address, or null to listen on all interfaces on port {@link MsrpConstants#DEFAULT_PORT}
     */
    public synchronized void setListenAddress(InetSocketAddress listenAddress) {
        this.listenAddress = listenAddress;
    }

    /**
     * Get the maximum number of allowed sessions. Default is {@value #DEFAULT_MAX_SESSIONS}.
     */
    public synchronized int getMaxSessions() {
        return this.maxSessions;
    }
    public synchronized void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    /**
     * Get the maximum allowed content length for incoming messages. Default is {@link MsrpInputParser#DEFAULT_MAX_CONTENT_LENGTH}.
     */
    public synchronized long getMaxContentLength() {
        return this.maxContentLength;
    }
    public synchronized void setMaxContentLength(long maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    /**
     * Get the maximum idle time for connections that have no associated sessions. Default is {@value #DEFAULT_MAX_IDLE_TIME}ms.
     */
    public synchronized long getMaxIdleTime() {
        return this.maxIdleTime;
    }
    public synchronized void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    /**
     * Get the outgoing connection timeout in milliseconds. Default is {@value #DEFAULT_CONNECT_TIMEOUT}ms.
     */
    public synchronized long getConnectTimeout() {
        return this.connectTimeout;
    }
    public synchronized void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Start this instance. Does nothing if already started.
     *
     * @throws IOException if server socket cannot be created
     */
    public synchronized void start() throws IOException {
        if (this.serviceThread != null)
            return;
        InetSocketAddress startListenAddress = this.listenAddress;
        if (startListenAddress == null)
            startListenAddress = new InetSocketAddress(MsrpConstants.DEFAULT_PORT);
        if (this.log.isDebugEnabled())
            this.log.debug("starting " + this + " listening on " + startListenAddress);
        boolean successful = false;
        try {
            this.selector = Selector.open();
            this.serverSocketChannel = ServerSocketChannel.open();
            this.serverSocketChannel.configureBlocking(false);
            this.serverSocketChannel.bind(startListenAddress);
            this.selectionKey = this.createSelectionKey(this.serverSocketChannel, new SelectorService() {
                @Override
                public void serviceIO(SelectionKey key) throws IOException {
                    if (key.isAcceptable())
                        Msrp.this.handleAccept();
                }
                @Override
                public void close(Exception cause) {
                    Msrp.this.log.error("stopping " + this + " due to exception", cause);
                    Msrp.this.stop();
                }
            });
            this.selectForAccept(true);
            this.serviceThread = new ServiceThread();
            this.serviceThread.start();
            successful = true;
        } finally {
            if (!successful)
                this.stop();
        }
    }

    /**
     * Stop this instance. Does nothing if already stopped.
     */
    public void stop() {
        Thread waitForThread = null;
        synchronized (this) {
            if (this.serviceThread != null && this.log.isDebugEnabled())
                this.log.debug("stopping " + this);
            if (this.serverSocketChannel != null) {
                try {
                    this.serverSocketChannel.close();
                } catch (IOException e) {
                    // ignore
                }
                this.serverSocketChannel = null;
            }
            if (this.selector != null) {
                try {
                    this.selector.close();
                } catch (IOException e) {
                    // ignore
                }
                this.selector = null;
            }
            if (this.serviceThread != null) {
                this.serviceThread.interrupt();
                if (!this.serviceThread.equals(Thread.currentThread()))
                    waitForThread = this.serviceThread;
                this.serviceThread = null;
            }
            this.selectionKey = null;
        }
        if (waitForThread != null) {
            try {
                waitForThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Create a new {@link Session} using the given local and remote URIs.
     *
     * <p>
     * Notification of {@link Session} events will be delivered to {@code listener} using the provided {@code callbackExecutor};
     * the {@code callbackExecutor} <b>must execute actions in a separate thread</b> from the one that invoked it in order
     * to avoid deadlocks and re-entrancy problems. For example, using an {@link Executor} returned by
     * {@link java.util.concurrent.Executors#newSingleThreadScheduledExecutor} is sufficient.
     * </p>
     *
     * @param localURI URI identifying the local side of the session
     * @param remoteURI URI identifying the remote side of the session
     * @param endpoint destination for outgoing TCP connection (if any), or null to infer from {@code remoteURI};
     *  ignored if {@code !active}
     * @param listener listener for session events
     * @param callbackExecutor executes listener callbacks; must do so in a separate thread
     * @param active true if this side is active and should initiate the connection, false to wait for the remote side connect to us
     * @return newly created session, or null if there are already too many existing sessions
     * @throws IllegalStateException if this instance is not {@linkplain #start started}
     * @throws IllegalArgumentException if any parameter other than {@code endpoint} is null
     * @throws IllegalArgumentException if a session corresponding to {@code localURI} already exists
     */
    public synchronized Session createSession(MsrpUri localURI, MsrpUri remoteURI, Endpoint endpoint,
      SessionListener listener, Executor callbackExecutor, boolean active) {

        // Sanity check
        if (localURI == null)
            throw new IllegalArgumentException("null localURI");
        if (remoteURI == null)
            throw new IllegalArgumentException("null remoteURI");
        if (listener == null)
            throw new IllegalArgumentException("null listener");
        if (callbackExecutor == null)
            throw new IllegalArgumentException("null callbackExecutor");
        if (this.serviceThread == null)
            throw new IllegalStateException("not started");

        // Infer endpoint if needed
        if (endpoint == null)
            endpoint = remoteURI.toEndpoint();

        // Check for duplicate session
        if (this.sessionMap.containsKey(localURI))
            throw new IllegalArgumentException("duplicate session local URI `" + localURI + "'");

        // Check session size limit
        if (this.sessionMap.size() >= this.maxSessions) {
            this.log.warn("too many MSRP connections (" + this.sessionMap.size() + " >= "
              + this.maxSessions + "), not creating any more");
            return null;
        }

        // Create new session
        final Session session = new Session(this, localURI, remoteURI, active ? endpoint : null, listener, callbackExecutor);
        this.sessionMap.put(localURI, session);

        // Logging
        if (this.log.isDebugEnabled())
            this.log.debug(this + " created new session " + session);

        // If session is passive, we wait for the remote side to connect to us before doing anything else
        if (!active)
            return session;

        // Re-use existing connection to this endpoint if one already exists
        for (Connection connection : this.connections) {
            if (connection.getEndpoint().equals(endpoint)) {
                if (this.log.isDebugEnabled())
                    this.log.debug(this + " binding " + session + " to existing " + connection);
                session.setConnection(connection);
                break;
            }
        }

        // Send an empty message to bind peer's connection to this session
        session.send(null, null);

        // Wakeup service thread
        this.wakeup();

        // Done
        return session;
    }

    /**
     * Get all known {@link Session}s keyed by local URI.
     * Note that as keys in the returned map, URI's are compared for equality according to RFC 4579, Section 6.1.
     *
     * @return mutable "snapshot" mapping from local URI to {@link Session}
     */
    public synchronized SortedMap<MsrpUri, Session> getSessions() {
        return new TreeMap<MsrpUri, Session>(this.sessionMap);
    }

    @Override
    public String toString() {
        return "Msrp[port=" + this.listenAddress.getPort() + "]";
    }

// Internal API

    // Create connection. Note: this can block doing DNS lookups XXX
    Connection createConnection(Endpoint endpoint) throws IOException {
        final SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        if (this.log.isDebugEnabled())
            this.log.debug(this + " looking up DNS name `" + endpoint.getHost() + "'");
        final InetSocketAddress socketAddress = endpoint.toSocketAddress();
        if (socketAddress.isUnresolved())
            throw new IOException("DNS lookup failure for `" + socketAddress.getHostString() + "'");
        if (this.log.isDebugEnabled()) {
            this.log.debug(this + ": `" + endpoint.getHost() + "' resolves to "
              + socketAddress.getAddress() + "; initiating connection");
        }
        socketChannel.connect(socketAddress);
        final Connection connection = new Connection(this, endpoint, socketChannel);
        this.connections.add(connection);
        return connection;
    }

    // Invoked when a message arrives on a connection
    void handleMessage(Connection connection, MsrpMessage message) throws IOException {

        // Find session
        final MsrpUri localURI = message.getHeaders().getToPath().get(0);
        final Session session = this.sessionMap.get(localURI);
        if (session == null) {
            if (message instanceof MsrpRequest) {
                connection.write(Session.createMsrpResponse((MsrpRequest)message,
                  MsrpConstants.RESPONSE_CODE_SESSION_DOES_NOT_EXIST, "Session does not exist"));
            }
            return;
        }

        // Verify/bind connection
        if (session.getConnection() == null) {
            if (this.log.isDebugEnabled())
                this.log.debug(this + " binding " + session + " to " + connection);
            session.setConnection(connection);
            this.wakeup();
        } else if (!session.getConnection().equals(connection)) {
            if (message instanceof MsrpRequest) {
                connection.write(Session.createMsrpResponse((MsrpRequest)message,
                  MsrpConstants.RESPONSE_CODE_SESSION_ALREADY_BOUND, "Session already bound to a different connection"));
            }
            return;
        }

        // Handle message
        session.handleMessage(message);
    }

    // Invoked when a connection closes
    void handleConnectionClosed(Connection connection, Exception cause) {
        if (this.log.isDebugEnabled())
            this.log.debug(this + " handling closed connection " + connection);
        for (Iterator<Session> i = this.sessionMap.values().iterator(); i.hasNext(); ) {
            final Session session = i.next();
            if (connection.equals(session.getConnection())) {
                i.remove();
                session.close(cause);
            }
        }
        this.connections.remove(connection);
        this.wakeup();
    }

    // Invoked when a session closes
    void handleSessionClosed(Session session) {
        if (this.log.isDebugEnabled())
            this.log.debug(this + " handling closed session " + session);
        this.sessionMap.remove(session.getLocalUri());
        this.wakeup();
    }

    // Invoked when a connection needs to select for I/O
    SelectionKey createSelectionKey(SelectableChannel channel, SelectorService service) throws ClosedChannelException {
        if (channel == null)
            throw new IllegalArgumentException("null channel");
        if (service == null)
            throw new IllegalArgumentException("null service");
        if (this.selector == null)
            return null;
        return channel.register(this.selector, 0, service);
    }

    // Invoked when we get an incoming connection
    private void handleAccept() throws IOException {

        // Check connection size limit
        if (this.connections.size() >= this.maxSessions) {
            this.log.warn("too many MSRP connections (" + this.connections.size() + " >= "
              + this.maxSessions + "), not accepting any more (for now)");
            this.selectForAccept(false);
            return;
        }

        // Accept connection
        final SocketChannel socketChannel = this.serverSocketChannel.accept();
        if (socketChannel == null)
            return;
        socketChannel.configureBlocking(false);

        // Get remote endpoint
        final InetSocketAddress remote = (InetSocketAddress)socketChannel.socket().getRemoteSocketAddress();
        final Endpoint endpoint = new Endpoint(remote.getHostString(), remote.getPort());
        if (this.log.isDebugEnabled())
            this.log.debug(this + " accepted incoming connection from " + endpoint);

        // Add new connection
        this.connections.add(new Connection(this, endpoint, socketChannel));
    }

    // Enable/disable incoming connections
    private void selectForAccept(boolean enabled) throws IOException {
        if (this.selectionKey == null)
            return;
        if (enabled && (this.selectionKey.interestOps() & SelectionKey.OP_ACCEPT) == 0) {
            this.selectionKey.interestOps(this.selectionKey.interestOps() | SelectionKey.OP_ACCEPT);
            if (this.log.isDebugEnabled())
                this.log.debug(this + " started listening for incoming connections");
        } else if (!enabled && (this.selectionKey.interestOps() & SelectionKey.OP_ACCEPT) != 0) {
            this.selectionKey.interestOps(this.selectionKey.interestOps() & ~SelectionKey.OP_ACCEPT);
            if (this.log.isDebugEnabled())
                this.log.debug(this + " stopped listening for incoming connections");
        }
    }

    // Wakeup service thread
    void wakeup() {
        if (this.log.isTraceEnabled())
            this.log.trace("wakeup service thread");
        if (this.selector != null)
            this.selector.wakeup();
    }

// Main service method

    private void service() throws IOException {
        while (true) {

            // Check if we're still open
            final Selector currentSelector;
            synchronized (this) {
                currentSelector = this.selector;
            }
            if (currentSelector == null)
                break;

            // Wait for I/O readiness, timeout, or shutdown
            try {
                if (this.log.isTraceEnabled())
                    this.log.trace("[SVC THREAD]: sleeping: keys=" + dbg(currentSelector.keys()));
                currentSelector.select(1000L);
            } catch (ClosedSelectorException e) {               // close() was invoked
                break;
            }
            if (Thread.interrupted())
                break;

            // Figure out what has happened
            synchronized (this) {

                // Are we shutting down?
                if (this.selector == null) {
                    for (Connection connection : new ArrayList<Connection>(this.connections))
                        connection.close(null);
                    break;
                }

                // Handle any ready I/O
                if (this.log.isTraceEnabled())
                    this.log.trace("[SVC THREAD]: awake: selectedKeys=" + dbg(currentSelector.selectedKeys()));
                for (Iterator<SelectionKey> i = selector.selectedKeys().iterator(); i.hasNext(); ) {
                    final SelectionKey key = i.next();
                    i.remove();
                    final SelectorService service = (SelectorService)key.attachment();
                    if (this.log.isTraceEnabled())
                        this.log.trace("[SVC THREAD]: I/O ready: key=" + dbg(key) + " service=" + service);
                    try {
                        service.serviceIO(key);
                    } catch (IOException e) {
                        if (this.log.isDebugEnabled())
                            this.log.debug("MSRP I/O error from " + service, e);
                        service.close(e);
                    } catch (Exception e) {
                        this.log.error("MSRP service error from " + service, e);
                        service.close(e);
                    }
                }

                // Perform session housekeeping
                final HashSet<Connection> activeConnections = new HashSet<>();
                for (Session session : new ArrayList<Session>(this.sessionMap.values())) {
                    if (session.getConnection() != null)
                        activeConnections.add(session.getConnection());
                    try {
                        session.performHousekeeping();
                    } catch (IOException e) {
                        if (this.log.isDebugEnabled())
                            this.log.debug("MSRP I/O error from " + session, e);
                        session.close(e);
                    } catch (Exception e) {
                        this.log.error("error performing housekeeping for " + session, e);
                        session.close(e);
                    }
                }

                // Perform connection housekeeping
                for (Connection connection : new ArrayList<Connection>(this.connections)) {
                    try {
                        connection.performHousekeeping(activeConnections.contains(connection));
                    } catch (IOException e) {
                        if (this.log.isDebugEnabled())
                            this.log.debug("MSRP I/O error from " + connection, e);
                        connection.close(e);
                    } catch (Exception e) {
                        this.log.error("error performing housekeeping for " + connection, e);
                        connection.close(e);
                    }
                }

                // Perform my own housekeeping
                this.selectForAccept(this.connections.size() < this.maxSessions);
            }
        }
    }

    private static String dbg(Iterable<? extends SelectionKey> keys) {
        final ArrayList<String> strings = new ArrayList<>();
        for (SelectionKey key : keys)
            strings.add(dbg(key));
        return strings.toString();
    }

    private static String dbg(SelectionKey key) {
        try {
            return "Key[interest=" + dbgOps(key.interestOps()) + ",ready="
              + dbgOps(key.readyOps()) + ",obj=" + key.attachment() + "]";
        } catch (java.nio.channels.CancelledKeyException e) {
            return "Key[canceled]";
        }
    }

    private static String dbgOps(int ops) {
        final StringBuilder buf = new StringBuilder(4);
        if ((ops & SelectionKey.OP_ACCEPT) != 0)
            buf.append("A");
        if ((ops & SelectionKey.OP_CONNECT) != 0)
            buf.append("C");
        if ((ops & SelectionKey.OP_READ) != 0)
            buf.append("R");
        if ((ops & SelectionKey.OP_WRITE) != 0)
            buf.append("W");
        return buf.toString();
    }

// ServiceThread

    private class ServiceThread extends Thread {

        public ServiceThread() {
            super("MSRP Service Thread for " + Msrp.this);
        }

        @Override
        public void run() {
            try {
                Msrp.this.service();
            } catch (ThreadDeath t) {
                throw t;
            } catch (Throwable t) {
                Msrp.this.log.error("unexpected error in service thread", t);
            }
            if (Msrp.this.log.isDebugEnabled())
                Msrp.this.log.debug(this + " exiting");
        }
    }
}

