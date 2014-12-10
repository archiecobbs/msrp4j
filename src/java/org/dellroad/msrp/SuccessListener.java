
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import org.dellroad.msrp.msg.ByteRange;

/**
 * Callback interface for MSRP success reports.
 */
public interface SuccessListener extends ReportListener {

    /**
     * Receive report of successful message transmission.
     *
     * @param session session on which the message was transmitted
     * @param messageId ID of message
     * @param byteRange confirmed byte range
     */
    void reportSuccess(Session session, String messageId, ByteRange byteRange);
}

