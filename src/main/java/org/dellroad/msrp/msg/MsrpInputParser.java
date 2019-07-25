
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp.msg;

import java.io.ByteArrayOutputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dellroad.msrp.MsrpConstants;
import org.dellroad.msrp.MsrpUri;

/**
 * Stateful MSRP message parser.
 *
 * <p>
 * Instances are configured with various size limits to avoid unbounded memory usage.
 * </p>
 */
public class MsrpInputParser {

    /** Default maximum line length in bytes ({@value #DEFAULT_MAX_LINE_LENGTH}) */
    public static final int DEFAULT_MAX_LINE_LENGTH = 16384;               // 16K

    /** Default maximum content length ({@value #DEFAULT_MAX_CONTENT_LENGTH}) */
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 16 * 1024 * 1024;  // 16M

    /** Default maximum number of URIs in {@code To-Path} or {@code From-Path} ({@value #DEFAULT_MAX_PATH_URIS}) */
    public static final int DEFAULT_MAX_PATH_URIS = 32;

    /** Default maximum number of MIME headers length ({@value #DEFAULT_MAX_MIME_HEADERS}) */
    public static final int DEFAULT_MAX_MIME_HEADERS = 16;

    /** Default maximum number of extension headers ({@value #DEFAULT_MAX_EXTENSION_HEADERS}) */
    public static final int DEFAULT_MAX_EXTENSION_HEADERS = 32;

    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("MSRP ([-.+%=\\p{Alnum}]{3,31}) ([A-Z]+)");
    private static final Pattern RESPONSE_LINE_PATTERN = Pattern.compile("MSRP ([-.+%=\\p{Alnum}]{3,31}) ([0-9]{3})( (.*))?");

    private static final Header HEADER_EOF = new Header("dummy", "dummy");

    private final LineInputParser lineParser;
    private final int maxBodySize;
    private final int maxPathUris;
    private final int maxMimeHeaders;
    private final int maxExtensionHeaders;

    private State state = State.FIRST_LINE;     // parse state
    private MsrpMessage message;                // the message we are building
    private String endLine;
    private boolean allowBody;
    private ByteArrayOutputStream body;
    private BoundaryInputParser boundaryInputParser;

    /**
     * Constructor. Uses default size limits.
     */
    public MsrpInputParser() {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_MAX_CONTENT_LENGTH,
          DEFAULT_MAX_PATH_URIS, DEFAULT_MAX_MIME_HEADERS, DEFAULT_MAX_EXTENSION_HEADERS);
    }

    /**
     * Primary constructor.
     *
     * @param maxLineLength maximum allowed header line length in bytes
     * @param maxBodySize maximum allowed body size in bytes
     * @param maxPathUris maximum number of URI's allowed in {@code To-Path} or {@code From-Path} header
     * @param maxMimeHeaders maximum number of allowed MIME headers
     * @param maxExtensionHeaders maximum number of allowed extension headers
     * @throws IllegalArgumentException if {@code input} is null
     */
    public MsrpInputParser(int maxLineLength, int maxBodySize, int maxPathUris, int maxMimeHeaders, int maxExtensionHeaders) {
        this.lineParser = new LineInputParser(maxLineLength);
        this.maxBodySize = maxBodySize;
        this.maxPathUris = maxPathUris;
        this.maxMimeHeaders = maxMimeHeaders;
        this.maxExtensionHeaders = maxExtensionHeaders;
    }

    /**
     * Input the next byte.
     *
     * @param b input byte
     * @return next complete message, or null if more bytes are needed
     * @throws ProtocolException if a protocol violation is detected
     */
    public MsrpMessage inputMessageByte(byte b) throws ProtocolException {

        // Input byte
        boolean complete = false;
        switch (this.state) {
        case FIRST_LINE:
            this.inputFirstLineByte(b);
            break;
        case TO_PATH:
            if (this.inputPathHeaderByte(this.message.getHeaders().getToPath(), MsrpConstants.TO_PATH_HEADER, b))
                this.state = State.FROM_PATH;
            break;
        case FROM_PATH:
            if (this.inputPathHeaderByte(this.message.getHeaders().getFromPath(), MsrpConstants.FROM_PATH_HEADER, b))
                this.state = State.HEADER;
            break;
        case HEADER:
            complete = this.inputHeaderByte(b);
            break;
        case BODY_CONTENT:
            complete = this.inputBodyContentByte(b);
            break;
        default:
            throw new RuntimeException("internal error");
        }

        // Message complete?
        if (complete) {
            final MsrpMessage result = this.message;
            this.reset();
            return result;
        }
        return null;
    }

    /**
     * Reset parse state.
     */
    public void reset() {
        this.lineParser.reset();
        this.state = State.FIRST_LINE;
        this.message = null;
        this.endLine = null;
        this.allowBody = false;
        this.body = null;
        this.boundaryInputParser = null;
    }

    /**
     * Determine whether this instance is sitting at a message boundary.
     *
     * <p>
     * This will be true after initial construction, an invocation of {@link #reset},
     * or an invocation {@link #inputMessageByte inputMessageByte()} that returned a non-null value.
     * </p>
     *
     * @return true if positioned at a message boundary
     */
    public boolean isBetweenMessages() {
        return this.state == State.FIRST_LINE && this.lineParser.isBetweenLines();
    }

// Internal parsing methods

    private void inputFirstLineByte(byte b) throws ProtocolException {

        // Read complete header line
        final String line = this.lineParser.inputLineByte(b);
        if (line == null)
            return;

        // Request or response?
        Matcher matcher;
        if ((matcher = REQUEST_LINE_PATTERN.matcher(line)).matches())
            this.message = new MsrpRequest(matcher.group(1), matcher.group(2), null);
        else if ((matcher = RESPONSE_LINE_PATTERN.matcher(line)).matches())
            this.message = new MsrpResponse(matcher.group(1), Integer.parseInt(matcher.group(2), 10), matcher.group(4), null);
        else
            throw new ProtocolException("invalid start line " + Util.quotrunc(line));

        // Prepare for parsing headers
        this.endLine = MsrpConstants.END_LINE_PREFIX + this.message.getTransactionId();
        this.allowBody = this.message instanceof MsrpRequest;

        // Update state
        this.state = State.TO_PATH;
    }

    private boolean inputHeaderByte(byte b) throws ProtocolException {

        // Read complete header
        final Header header = this.inputHeaderByteForHeader(b);
        if (header == null)
            return false;

        // No more headers?
        if (header == HEADER_EOF) {

            // Is message allowed to have a body?
            if (this.body != null && !this.allowBody)
                throw new ProtocolException("message must not contain a body but does");

            // If there is no body, we're done
            if (this.body == null)
                return true;

            // Start parsing body
            this.boundaryInputParser = new BoundaryInputParser(this.message.getTransactionId());
            this.state = State.BODY_CONTENT;
            return false;
        }

        // Handle header
        final String name = header.getName();
        final String value = header.getValue();
        if (name.equalsIgnoreCase(MsrpConstants.MESSAGE_ID_HEADER)) {
            if (!Pattern.compile(Util.IDENT_REGEX).matcher(value).matches())
                throw new ProtocolException("invalid `" + name + "' header value " + Util.quotrunc(value));
            this.message.getHeaders().setMessageId(value);
        } else if (name.equalsIgnoreCase(MsrpConstants.SUCCESS_REPORT_HEADER)) {
            switch (value) {
            case MsrpConstants.YES_HEADER_VALUE:
                this.message.getHeaders().setSuccessReport(true);
                break;
            case MsrpConstants.NO_HEADER_VALUE:
                this.message.getHeaders().setSuccessReport(false);
                break;
            default:
                throw new ProtocolException("invalid `" + name + "' header value " + Util.quotrunc(value));
            }
        } else if (name.equalsIgnoreCase(MsrpConstants.FAILURE_REPORT_HEADER)) {
            switch (value) {
            case MsrpConstants.YES_HEADER_VALUE:
                this.message.getHeaders().setFailureReport(FailureReport.YES);
                break;
            case MsrpConstants.NO_HEADER_VALUE:
                this.message.getHeaders().setFailureReport(FailureReport.NO);
                break;
            case MsrpConstants.PARTIAL_HEADER_VALUE:
                this.message.getHeaders().setFailureReport(FailureReport.PARTIAL);
                break;
            default:
                throw new ProtocolException("invalid `" + name + "' header value " + Util.quotrunc(value));
            }
        } else if (name.equalsIgnoreCase(MsrpConstants.BYTE_RANGE_HEADER)) {
            try {
                this.message.getHeaders().setByteRange(ByteRange.fromString(value));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("invalid `" + name + "' header value " + Util.quotrunc(value));
            }
        } else if (name.equalsIgnoreCase(MsrpConstants.STATUS_HEADER)) {
            try {
                this.message.getHeaders().setStatus(Status.fromString(value));
            } catch (IllegalArgumentException e) {
                throw new ProtocolException("invalid `" + name + "' header value " + Util.quotrunc(value));
            }
        } else if (name.equalsIgnoreCase(MsrpConstants.CONTENT_TYPE_HEADER))
            this.message.getHeaders().setContentType(value);
        else {
            if (MsrpRequest.isMimeHeader(name)) {
                this.message.getHeaders().getMimeHeaders().remove(header);               // ensure last one wins
                if (this.message.getHeaders().getMimeHeaders().size() >= this.maxMimeHeaders)
                    throw new ProtocolException("too many MIME headers (maximum " + this.maxMimeHeaders + ")");
                this.message.getHeaders().getMimeHeaders().add(header);
            } else {
                this.message.getHeaders().getExtensionHeaders().remove(header);          // ensure last one wins
                if (this.message.getHeaders().getExtensionHeaders().size() >= this.maxExtensionHeaders)
                    throw new ProtocolException("too many extension headers (maximum " + this.maxExtensionHeaders + ")");
                this.message.getHeaders().getExtensionHeaders().add(header);
            }
        }

        // Done
        return false;
    }

    // Input body content byte, returning true if message is complete
    private boolean inputBodyContentByte(byte b) throws ProtocolException {

        // Input body byte
        final byte[] data = this.boundaryInputParser.inputContentByte(b);
        if (data != null) {
            this.body.write(data, 0, data.length);
            if (this.body.size() > this.maxBodySize)
                throw new ProtocolException("body size exceeds maximum size limit (" + this.maxBodySize + " bytes)");
            return false;
        }

        // Add body to message and set flags
        final MsrpRequest request = (MsrpRequest)this.message;
        request.setBody(this.body.toByteArray());
        switch (this.boundaryInputParser.getFlagByte()) {
        case MsrpConstants.FLAG_INCOMPLETE:
            request.setComplete(false);
            request.setAborted(false);
            break;
        case MsrpConstants.FLAG_COMPLETE:
            request.setComplete(true);
            break;
        case MsrpConstants.FLAG_ABORT:
            request.setAborted(true);
            break;
        default:
            throw new RuntimeException("internal error");
        }

        // Done
        return true;
    }

    // Input required path header byte, returning true if header is complete
    private boolean inputPathHeaderByte(List<MsrpUri> uriList, String name, byte b) throws ProtocolException {

        // Get complete path(s)
        final String paths = this.inputRequiredHeaderByte(name, b);
        if (paths == null)
            return false;

        // Must be at least one
        if (paths.length() == 0)
            throw new ProtocolException("invalid empty `" + name + "' header");

        // Parse path into URI's
        int end;
        for (int start = 0; start < paths.length(); start = end) {
            if ((end = paths.indexOf(' ', start)) == -1)
                end = paths.length();
            final String uri = paths.substring(start, end);
            if (uriList.size() >= this.maxPathUris)
                throw new ProtocolException("too many URI's in `" + name + "' header (maximum " + this.maxPathUris + ")");
            try {
                uriList.add(new MsrpUri(uri));
            } catch (URISyntaxException e) {
                throw new ProtocolException("invalid URI " + Util.quotrunc(uri) + " in `" + name + "' header", e);
            }
        }

        // Done
        return true;
    }

    // Input required header byte, returning header value if header is complete
    private String inputRequiredHeaderByte(String name, byte b) throws ProtocolException {
        final Header header = this.inputHeaderByteForHeader(b);
        if (header == null)
            return null;
        if (header == HEADER_EOF)
            throw new ProtocolException("missing required `" + name + "' header");
        if (!header.getName().equalsIgnoreCase(name)) {
            throw new ProtocolException("expected required `" + name + "' header but found "
              + Util.quotrunc(header.getName()) + " header instead");
        }
        return header.getValue();
    }

    // Input header byte, returning null if incomplete, header if complete, or HEADER_EOF if none remain (and setting this.body)
    private Header inputHeaderByteForHeader(byte b) throws ProtocolException {

        // Read complete header line
        final String line = this.lineParser.inputLineByte(b);
        if (line == null)
            return null;

        // End line?
        assert this.endLine != null;
        if (line.startsWith(this.endLine) && line.length() == this.endLine.length() + 1) {
            final char flag = line.charAt(this.endLine.length());
            if (flag != (char)MsrpConstants.FLAG_COMPLETE)
                throw new ProtocolException("invalid end-line flag byte `" + flag + "' in message without body");
            this.body = null;
            return HEADER_EOF;
        }

        // Blank line?
        if (line.length() == 0) {
            this.body = new ByteArrayOutputStream();
            return HEADER_EOF;
        }

        // Parse header
        final Matcher matcher = Pattern.compile(Util.HEADER_REGEX).matcher(line);
        if (!matcher.matches())
            throw new ProtocolException("invalid header line " + Util.quotrunc(line));
        return new Header(matcher.group(1), matcher.group(2));
    }

// Parse states

    private enum State {
        FIRST_LINE,
        TO_PATH,
        FROM_PATH,
        HEADER,
        BODY_CONTENT;
    }
}

