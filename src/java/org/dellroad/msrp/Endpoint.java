
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import java.net.InetSocketAddress;

/**
 * Combination of a host and port.
 *
 * <p>
 * Instances are immutable.
 * </p>
 */
public class Endpoint {

    private final String host;
    private final int port;

    /**
     * Constructor.
     *
     * @param host remote host
     * @param port TCP port
     * @throws IllegalArgumentException if {@code host} is null
     * @throws IllegalArgumentException if {@code port} is not in the range 1-65535
     */
    public Endpoint(String host, int port) {
        if (host == null)
            throw new IllegalArgumentException("null host");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("invalid port " + port);
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    /**
     * Convert this instance to a {@link InetSocketAddress}. This may result in a DNS lookup.
     * If the lookup fails, the returned {@link InetSocketAddress} will be <i>unresolved</i>.
     */
    public InetSocketAddress toSocketAddress() {
        return new InetSocketAddress(this.host, this.port);
    }

    @Override
    public String toString() {
        return this.host + ":" + this.port;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final Endpoint that = (Endpoint)obj;
        return this.host.equals(that.host) && this.port == that.port;
    }

    @Override
    public int hashCode() {
        return this.host.hashCode() ^ this.port;
    }
}

