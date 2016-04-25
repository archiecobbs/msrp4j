
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import org.dellroad.msrp.MsrpConstants;

/**
 * MSRP failure report values.
 */
public enum FailureReport {
    NO(MsrpConstants.NO_HEADER_VALUE),
    YES(MsrpConstants.YES_HEADER_VALUE),
    PARTIAL(MsrpConstants.PARTIAL_HEADER_VALUE);

    private final String headerValue;

    FailureReport(String headerValue) {
        this.headerValue = headerValue;
    }

    /**
     * Get the header value that represents this instance.
     */
    @Override
    public String toString() {
        return this.headerValue;
    }
}

