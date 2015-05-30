
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an MRSP URI. This class only supports URIs that have session ID's.
 */
public class MsrpUri {

    private final URI uri;
    private final String sessionId;
    private final String transport;
    private final Set<String> parameters;

    /**
     * Constrcuctor.
     *
     * @throws URISyntaxException if {@code string} is not a valid MSRP URI
     */
    public MsrpUri(String string) throws URISyntaxException {

        // Parse URI
        this.uri = new URI(string);

        // Basic structural checking
        final String scheme = this.uri.getScheme();
        if (scheme == null)
            throw new URISyntaxException(string, "invalid MSRP URI: no scheme");
        if (!MsrpConstants.MSRP_SCHEME.equalsIgnoreCase(scheme) && !MsrpConstants.MSRPS_SCHEME.equalsIgnoreCase(scheme))
            throw new URISyntaxException(string, "invalid MSRP URI: unknown scheme `" + scheme + "'");
        if (this.uri.getAuthority() == null)
            throw new URISyntaxException(string, "invalid MSRP URI: missing authority");
        if (this.uri.getQuery() != null)
            throw new URISyntaxException(string, "invalid MSRP URI: query string is not allowed");
        if (this.uri.getFragment() != null)
            throw new URISyntaxException(string, "invalid MSRP URI: fragment is not allowed");
        final String path = this.uri.getPath();
        if (path == null || path.length() == 0 || path.charAt(0) != '/')
            throw new URISyntaxException(string, "invalid MSRP URI: no path, or path does not start with `/'");

        // Parse session ID, transport, and parameters out from path
        final List<String> plist = new ArrayList<>(3);
        for (int pos = 1; pos <= path.length(); ) {
            int end = path.indexOf(';', pos);
            if (end == -1)
                end = path.length();
            plist.add(path.substring(pos, end));
            pos = end + 1;
        }
        switch (plist.size()) {
        case 0:
            throw new URISyntaxException(string, "invalid MSRP URI: no session ID specified");
        case 1:
            throw new URISyntaxException(string, "invalid MSRP URI: no transport specified");
        default:
            break;
        }
        this.sessionId = plist.get(0);
        this.transport = plist.get(1);
        this.parameters = Collections.unmodifiableSet(new LinkedHashSet<String>(plist.subList(2, plist.size())));

        // Verify transport is "tcp"
        if (!this.transport.equals(MsrpConstants.TRANSPORT_TCP))
            throw new URISyntaxException(string, "invalid MSRP URI: unknown transport `" + this.transport + "'");
    }

    /**
     * Get this instance as an {@link URI}.
     */
    public URI getUri() {
        return this.uri;
    }

    /**
     * Determine whether this URI uses TLS.
     */
    public boolean isSecure() {
        return MsrpConstants.MSRPS_SCHEME.equalsIgnoreCase(this.uri.getScheme());
    }

    /**
     * Get the MSRP session ID specified in this URI.
     */
    public String getSessionId() {
        return this.sessionId;
    }

    /**
     * Get the MSRP transport specified in this URI. Currently will always be {@link MsrpConstants#TRANSPORT_TCP}.
     */
    public String getTransport() {
        return this.transport;
    }

    /**
     * Get the URI-parameters, if any. The returned {@link Set} will iterate the parameters in their original order.
     *
     * @return unmodifiable set of URI-parameters, possibly empty
     */
    public Set<String> getParameters() {
        return this.parameters;
    }

    /**
     * Create an {@link Endpoint} corresponding to this instance.
     */
    public Endpoint toEndpoint() {
        int port = this.uri.getPort();
        if (port == -1)
            port = MsrpConstants.DEFAULT_PORT;
        return new Endpoint(this.uri.getHost(), port);
    }

// Object

    @Override
    public String toString() {
        return this.uri.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final MsrpUri that = (MsrpUri)obj;
        return this.uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return this.uri.hashCode();
    }
}

