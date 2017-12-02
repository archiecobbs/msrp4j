
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MSRP {@code Byte-Range} header value.
 *
 * <p>
 * Instances are immutable.
 * </p>
 */
public class ByteRange {

    /**
     * Used for empty messages: {@code 1-0/0}.
     */
    public static final ByteRange EMPTY = new ByteRange(1, 0, 0);

    /**
     * Used for indeterminate "all" byte range: {@code 1-*}{@code /*}.
     */
    public static final ByteRange ALL = new ByteRange(1, -1, -1);

    private static final String BYTE_RANGE_REGEX = "([0-9]+)-(([0-9]+)|\\*)/(([0-9]+)|\\*)";

    private final long start;
    private final long end;
    private final long total;

    /**
     * Constructs an instance indicating the entire content with the specified length.
     * Equivalent to {@code ByteRange(1, total, total)}.
     *
     * @param total total number of bytes, or -1 if unspecified
     * @throws IllegalArgumentException if {@code total} is less than -1
     */
    public ByteRange(long total) {
        this(1, total, total);
    }

    /**
     * Constructor.
     *
     * @param start start byte position (1-based)
     * @param end end byte position (1-based), or -1 if unspecified
     * @param total total number of bytes, or -1 if unspecified
     * @throws IllegalArgumentException if {@code start} is less than 1
     * @throws IllegalArgumentException if {@code end} or {@code total} is less than -1
     * @throws IllegalArgumentException if {@code end} is not -1 and {@code start - 1} is greater than {@code end}
     * @throws IllegalArgumentException if {@code total} is not -1 and {@code end} is greater than {@code total}
     */
    public ByteRange(long start, long end, long total) {
        if (start < 1)
            throw new IllegalArgumentException("start < 1");
        if (end < -1)
            throw new IllegalArgumentException("end < -1");
        if (total < -1)
            throw new IllegalArgumentException("total < -1");
        if (end != -1 && start - 1 > end)
            throw new IllegalArgumentException("start - 1 > end");
        if (total != -1 && end > total)
            throw new IllegalArgumentException("end > total");
        this.start = start;
        this.end = end;
        this.total = total;
    }

    /**
     * Get the start byte position.
     *
     * @return start byte position, at least one
     */
    public long getStart() {
        return this.start;
    }

    /**
     * Get the end byte position.
     *
     * @return end byte position, or -1 if not specified
     */
    public long getEnd() {
        return this.end;
    }

    /**
     * Get the total number of bytes.
     *
     * @return total number of bytes, or -1 if not specified
     */
    public long getTotal() {
        return this.total;
    }

    /**
     * Create an instance by parsing a {@link String}.
     *
     * @param string byte range expressed as a string
     * @return corresponding {@link ByteRange}
     * @throws IllegalArgumentException if {@code string} is null or invalid
     */
    public static ByteRange fromString(String string) {
        if (string == null)
            throw new IllegalArgumentException("null string");
        final Matcher matcher = Pattern.compile(BYTE_RANGE_REGEX).matcher(string);
        if (!matcher.matches())
            throw new IllegalArgumentException("invalid byte range " + Util.quotrunc(string));
        try {
            final long start = Long.parseLong(matcher.group(1), 10);
            final long end = !matcher.group(2).equals("*") ? Long.parseLong(matcher.group(3), 10) : -1;
            final long total = !matcher.group(4).equals("*") ? Long.parseLong(matcher.group(5), 10) : -1;
            return new ByteRange(start, end, total);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid byte range " + Util.quotrunc(string));
        }
    }

// Object

    @Override
    public String toString() {
        return this.start + "-" + (this.end != -1 ? this.end : "*") + "/" + (this.total != -1 ? this.total : "*");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final ByteRange that = (ByteRange)obj;
        return this.start == that.start && this.end == that.end && this.total == that.total;
    }

    @Override
    public int hashCode() {
        return (int)((this.start << 22) ^ (this.end << 11) ^ this.total);
    }
}

