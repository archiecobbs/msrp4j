
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.dellroad.msrp.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class BoundaryInputStreamTest extends TestSupport {

    @Test(dataProvider = "cases")
    public <T extends MsrpMessage> void testValid(String filename, String txid, int length, char flag, String after)
      throws Exception {

        // Read data
        final byte[] data = this.readResource(filename);

        // Read data up until boundary or EOFException
        final ByteArrayInputStream input = new ByteArrayInputStream(data);
        final BoundaryInputStream boundaryInput = new BoundaryInputStream(input, txid);
        final byte[] body;
        try {
            body = this.readAll(boundaryInput);
            Assert.assertNotEquals(length, -1, "expected EOFException but read " + body.length + " bytes successfully");
        } catch (EOFException e) {
            if (length != -1)
                throw e;
            return;
        }

        // Verify that the correct data was read
        Assert.assertEquals(body, Arrays.copyOfRange(data, 0, length));

        // Verify we got the right flag
        Assert.assertEquals(boundaryInput.getFlagByte(), (byte)flag);

        // Verify we can continue reading after the end of the boundary
        final String remain = new String(this.readAll(input), "UTF-8");
        Assert.assertEquals(remain, after);
    }

    private byte[] readAll(InputStream input) throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final byte[] tmp = new byte[1024];
        for (int r; (r = input.read(tmp)) != -1; )
            buf.write(tmp, 0, r);
        return buf.toByteArray();
    }

    @DataProvider(name = "cases")
    public Object[][] validCases() throws IOException {
        return new Object[][] {
            { "boundary-match.in",      "abcd1234",     0x01d8, '$', "\r\nhere's some junk after the boundary\r\n"},
            { "boundary-match.in",      "abcd123",      -1, '?', null },
            { "boundary-match.in",      "abcd12345",    -1, '?', null },
            { "boundary-match.in",      "bcd1234",      -1, '?', null },
            { "boundary-aborted.in",    "aaabaaaa",     0x0030, '#', "after stuff\r\n"},
            { "boundary-aborted.in",    "aaaabaaa",     -1, '?', null },
            { "boundary-incomplete.in", "aaabaaaa",     0x0030, '+', "after stuff\r\n"},
            { "boundary-incomplete.in", "aaaabaaa",     -1, '?', null },
        };
    }
}

