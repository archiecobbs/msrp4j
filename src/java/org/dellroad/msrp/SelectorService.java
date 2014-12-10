
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp;

import java.io.IOException;
import java.nio.channels.SelectionKey;

interface SelectorService {

    /**
     * Handle ready I/O.
     *
     * @param key selection key
     */
    void serviceIO(SelectionKey key) throws IOException;

    /**
     * Close this service due to an error. Must be idempotent.
     */
    void close(Exception cause);
}

