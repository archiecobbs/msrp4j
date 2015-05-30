
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Generic MSRP header.
 *
 * <p>
 * Instances are immutable. When compared using {@link #equals equals()}, names and values are compared case-sensitively.
 * However in practice header names are compared case-insensitively; use {@link #SORT_BY_NAME} for these comparisons.
 * </p>
 */
public class Header {

    public static final Comparator<Header> SORT_BY_NAME = new Comparator<Header>() {
        @Override
        public int compare(Header header1, Header header2) {
            return header1.getName().toLowerCase().compareTo(header2.getName().toLowerCase());
        }
    };

    private final String name;
    private final String value;

    /**
     * Constructor.
     *
     * @param name header name
     * @param value header value
     * @throws IllegalArgumentException if either parameter is null
     * @throws IllegalArgumentException if either parameter is invalid
     */
    public Header(String name, String value) {
        if (name == null)
            throw new IllegalArgumentException("null name");
        if (!Pattern.compile(Util.HEADER_NAME_REGEX).matcher(name).matches())
            throw new IllegalArgumentException("invalid header name `" + name + "'");
        if (value == null)
            throw new IllegalArgumentException("null value");
        if (!Pattern.compile(Util.HEADER_VALUE_REGEX).matcher(value).matches())
            throw new IllegalArgumentException("invalid header value `" + value + "'");
        this.name = name;
        this.value = value;
    }

    /**
     * Get the header name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the header value.
     */
    public String getValue() {
        return this.value;
    }

// Object

    @Override
    public String toString() {
        return String.format("%s: %s", this.name, this.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final Header that = (Header)obj;
        return this.name.equals(that) && this.value.equals(value);
    }

    @Override
    public int hashCode() {
        return this.name.hashCode() ^ this.value.hashCode();
    }
}

