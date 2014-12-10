
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.IOException;

/**
 * Exception thrown by a {@link BoundaryInputParser}, {@link LineInputParser}, or {@link MsrpInputParser}
 * when a protocol violation is detected or a size limit is exceeded.
 */
@SuppressWarnings("serial")
public class ProtocolException extends IOException {

    public ProtocolException() {
    }

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProtocolException(Throwable cause) {
        super(cause);
    }
}

