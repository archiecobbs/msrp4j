
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MSRP {@code Status} header.
 *
 * <p>
 * Instances are immutable.
 * </p>
 */
public class Status {

    private static final String STATUS_REGEX = "([0-9]{3}) ([0-9]{3})( (.*))?";

    private final int namespace;
    private final int code;
    private final String comment;

    /**
     * Constructor.
     *
     * @param code status code
     * @param comment comment, or null
     */
    public Status(int code, String comment) {
        this(0, code, comment);
    }

    /**
     * Constructor.
     *
     * @param namespace status code namespace
     * @param code status code
     */
    public Status(int namespace, int code) {
        this(namespace, code, null);
    }

    /**
     * Constructor.
     *
     * @param namespace status code namespace
     * @param code status code
     * @param comment comment, or null
     */
    public Status(int namespace, int code, String comment) {
        if (namespace < 0 || namespace > 999)
            throw new IllegalArgumentException("invalid namespace " + namespace);
        if (code < 0 || code > 999)
            throw new IllegalArgumentException("invalid code " + code);
        this.namespace = namespace;
        this.code = code;
        this.comment = comment;
    }

    /**
     * Get the status code namespace.
     */
    public int getNamespace() {
        return this.namespace;
    }

    /**
     * Get the status code.
     */
    public int getCode() {
        return this.code;
    }

    /**
     * Get the comment.
     *
     * @return comment, or null if there is no comment
     */
    public String getComment() {
        return this.comment;
    }

    /**
     * Create an instance by parsing a {@link String}.
     *
     * @param string static expressed as a string
     * @throws IllegalArgumentException if {@code string} is null or invalid
     */
    public static Status fromString(String string) {
        if (string == null)
            throw new IllegalArgumentException("null string");
        final Matcher matcher = Pattern.compile(STATUS_REGEX).matcher(string);
        if (!matcher.matches())
            throw new IllegalArgumentException("invalid status " + Util.quotrunc(string));
        try {
            final int namespace = Integer.parseInt(matcher.group(1), 10);
            final int code = Integer.parseInt(matcher.group(2), 10);
            return new Status(namespace, code, matcher.group(4));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid status " + Util.quotrunc(string));
        }
    }

// Object

    @Override
    public String toString() {
        return String.format("%03d %03d%s", this.namespace, this.code, this.comment != null ? " " + this.comment : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final Status that = (Status)obj;
        return this.namespace == that.namespace
          && this.code == that.code
          && (this.comment != null ? this.comment.equals(that.comment) : that.comment == null);
    }

    @Override
    public int hashCode() {
        return (this.namespace << 16) ^ (this.code << 8) ^ (this.comment != null ? this.comment.hashCode() : 0);
    }
}

