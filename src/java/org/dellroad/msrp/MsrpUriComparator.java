
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.net.URI;
import java.util.Comparator;

/**
 * Compares MRSP URI's for equality according to RFC 4579, Section 6.1.
 */
public final class MsrpUriComparator implements Comparator<MsrpUri> {

    public static final MsrpUriComparator INSTANCE = new MsrpUriComparator();

    private MsrpUriComparator() {
    }

    @Override
    public int compare(MsrpUri msrp1, MsrpUri msrp2) {
        final URI uri1 = msrp1.getUri();
        final URI uri2 = msrp2.getUri();
        int diff = uri1.getScheme().toLowerCase().compareTo(uri2.getScheme().toLowerCase());
        if (diff != 0)
            return diff;
        diff = uri1.getHost().toLowerCase().compareTo(uri2.getHost().toLowerCase());
        if (diff != 0)
            return diff;
        diff = Integer.compare(uri1.getPort(), uri2.getPort());
        if (diff != 0)
            return diff;
        diff = msrp1.getSessionId().compareTo(msrp2.getSessionId());
        if (diff != 0)
            return diff;
        diff = msrp1.getTransport().compareTo(msrp2.getTransport());
        if (diff != 0)
            return diff;
        return 0;
    }
}

