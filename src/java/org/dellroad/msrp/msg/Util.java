
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.nio.charset.Charset;

final class Util {

    public static final String TOKEN_REGEX = "[-!#$%&'*+0-9A-Za-z^_`{|}~]";
    public static final String IDENT_REGEX = "\\p{Alnum}[-\\p{Alnum}.+%=]{3,31}";
    public static final String METHOD_REGEX = "[A-Z]+";
    public static final String UTF8TEXT_REGEX = "[\\t\u0020-\uffff]";
    public static final String HEADER_NAME_REGEX = "\\p{Alpha}" + TOKEN_REGEX + "*";
    public static final String HEADER_VALUE_REGEX = UTF8TEXT_REGEX + "*";
    public static final String HEADER_REGEX = "(" + HEADER_NAME_REGEX + "): (" + HEADER_VALUE_REGEX + ")";

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final String CRLF = "\r\n";

    public static final int TRUNCATE_LENGTH = 40;

    private Util() {
    }

    public static String quotrunc(String text) {
        final String trunc = text.length() <= TRUNCATE_LENGTH ? text : text.substring(0, TRUNCATE_LENGTH - 3) + "...";
        return "`" + trunc + "'";
    }
}

