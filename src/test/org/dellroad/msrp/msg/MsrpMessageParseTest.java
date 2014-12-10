
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.dellroad.msrp.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class MsrpMessageParseTest extends TestSupport {

// VALID test cases

    @Test(dataProvider = "validCases")
    public <T extends MsrpMessage> void testValid(String filename, Class<T> type) throws Exception {

        // Parse exactly one message from file
        final T msg1 = this.readOneMessage(new ByteArrayInputStream(this.readResource(filename)), type);

        // Encode and decode message and verify its the same as before
        final byte[] buf = msg1.encode(true);
        final T msg2 = this.readOneMessage(new ByteArrayInputStream(buf), type);
        Assert.assertEquals(msg1, msg2);
    }

    @DataProvider(name = "validCases")
    public Object[][] validCases() throws IOException {
        return new Object[][] {
            { "request-valid-1.in", MsrpRequest.class },
        };
    }

// INVALID test cases

    @Test(dataProvider = "invalidCases")
    public void testInvalid(String filename, String errpat) throws Exception {
        try {
            final MsrpMessage msg = this.readOneMessage(new ByteArrayInputStream(this.readResource(filename)), MsrpMessage.class);
            throw new Exception("expected exception but got " + msg);
        } catch (ProtocolException e) {
            if (e.getMessage() == null || !Pattern.compile(errpat).matcher(e.getMessage()).matches())
                throw new Exception("expected exception message matching `" + errpat + "' but was `" + e.getMessage() + "'");
        }
    }

    // Read exactly one message from input
    private <T extends MsrpMessage> T readOneMessage(InputStream input, Class<T> type) throws IOException {
        final MsrpInputStream msrp = new MsrpInputStream(input);
        final MsrpMessage msg = msrp.readMsrpMessage();
        Assert.assertTrue(type.isInstance(msg), "expected type " + type.getSimpleName() + " but got " + msg);
        Assert.assertNull(msrp.readMsrpMessage());
        msrp.close();
        return type.cast(msg);
    }

    @DataProvider(name = "invalidCases")
    public Object[][] invalidCases() throws IOException {
        return new Object[][] {
            { "message-invalid-1.in",   "invalid start line.*" },
            { "message-invalid-2.in",   "MIME headers are not allowed when message has no body" },
            { "message-invalid-3.in",   "invalid end-line flag byte.*" },
        };
    }
}

