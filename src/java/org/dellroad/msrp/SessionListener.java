
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.util.List;
import java.util.SortedSet;

import org.dellroad.msrp.msg.Header;

/**
 * Callback interface for MSRP {@link Session}s.
 */
public interface SessionListener {

    /**
     * Notification that a {@link Session} has been closed or failed due to an error.
     *
     * @param session the session that closed
     * @param cause the error that occurred, or null if the session was closed manually by the application
     */
    void sessionClosed(Session session, Exception cause);

    /**
     * Notification that an MSRP message has been received in a session.
     *
     * <p>
     * Note that this method is responsible for (eventually) triggering a success or failure report
     * depending on {@code successReport} and/or {@code failureReport}.
     * </p>
     *
     * @param session the session on which the message was received
     * @param fromPath the path that the message took to get here
     * @param messageId sender's unique ID for this message
     * @param content message content, or null if message contained no content
     * @param contentType message content type, or null if message contained no content
     * @param headers other headers (including any MIME headers) sorted by name
     * @param successReport whether a success report is requested by the sender
     * @param failureReport whether a failure report is requested by the sender
     * @see Session#sendSuccessReport Session.sendSuccessReport()
     * @see Session#sendFailureReport Session.sendFailureReport()
     */
    void sessionReceivedMessage(Session session, List<MsrpUri> fromPath, String messageId, byte[] content, String contentType,
      SortedSet<Header> headers, boolean successReport, boolean failureReport);
}

