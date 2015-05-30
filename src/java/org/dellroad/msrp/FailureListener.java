
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

import org.dellroad.msrp.msg.Status;

/**
 * Callback interface for MSRP failure reports.
 */
public interface FailureListener extends ReportListener {

    /**
     * Receive report of failed message transmission.
     *
     * @param session session on which the message was transmitted
     * @param messageId ID of message
     * @param status failure status
     */
    void reportFailure(Session session, String messageId, Status status);
}

