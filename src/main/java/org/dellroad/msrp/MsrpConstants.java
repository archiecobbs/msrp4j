
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.msrp;

/**
 * MSRP constants.
 */
public final class MsrpConstants {

    /**
     * Default TCP port for MSRP connections.
     */
    public static final int DEFAULT_PORT = 2855;

    /**
     * The {@code msrp} URI scheme.
     */
    public static final String MSRP_SCHEME = "msrp";

    /**
     * The {@code msrps} URI scheme.
     */
    public static final String MSRPS_SCHEME = "msrps";

    /**
     * The {@code tcp} transport.
     */
    public static final String TRANSPORT_TCP = "tcp";

    /**
     * The {@value #TO_PATH_HEADER} header name.
     */
    public static final String TO_PATH_HEADER = "To-Path";

    /**
     * The {@value #FROM_PATH_HEADER} header name.
     */
    public static final String FROM_PATH_HEADER = "From-Path";

    /**
     * The {@value #MESSAGE_ID_HEADER} header name.
     */
    public static final String MESSAGE_ID_HEADER = "Message-ID";

    /**
     * The {@value #SUCCESS_REPORT_HEADER} header name.
     */
    public static final String SUCCESS_REPORT_HEADER = "Success-Report";

    /**
     * The {@value #FAILURE_REPORT_HEADER} header name.
     */
    public static final String FAILURE_REPORT_HEADER = "Failure-Report";

    /**
     * The {@value #BYTE_RANGE_HEADER} header name.
     */
    public static final String BYTE_RANGE_HEADER = "Byte-Range";

    /**
     * The {@value #STATUS_HEADER} header name.
     */
    public static final String STATUS_HEADER = "Status";

    /**
     * The prefix of all MIME headers.
     */
    public static final String MIME_CONTENT_HEADER_PREFIX = "Content-";

    /**
     * The {@value #CONTENT_TYPE_HEADER} header name.
     */
    public static final String CONTENT_TYPE_HEADER = "Content-Type";

    /**
     * The {@value #CONTENT_ID_HEADER} header name.
     */
    public static final String CONTENT_ID_HEADER = "Content-ID";

    /**
     * The {@value #CONTENT_DESCRIPTION_HEADER} header name.
     */
    public static final String CONTENT_DESCRIPTION_HEADER = "Content-Description";

    /**
     * The {@value #CONTENT_DISPOSITION_HEADER} header name.
     */
    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";

    /**
     * The namespace for {@link org.dellroad.msrp.msg.Status} codes defined by RFC 4975 (value is zero, i.e., {@code "000"}).
     */
    public static final int RFC4975_STATUS_NAMESPACE = 0;

    /**
     * Header value {@link #YES_HEADER_VALUE}.
     */
    public static final String YES_HEADER_VALUE = "yes";

    /**
     * Header value {@link #NO_HEADER_VALUE}.
     */
    public static final String NO_HEADER_VALUE = "no";

    /**
     * Header value {@link #PARTIAL_HEADER_VALUE}.
     */
    public static final String PARTIAL_HEADER_VALUE = "partial";

    /**
     * End line seven dashes prefix.
     */
    public static final String END_LINE_PREFIX = "-------";

    /**
     * End-line flag for completed message.
     */
    public static final byte FLAG_COMPLETE = (byte)'$';

    /**
     * End-line flag for incomplete message.
     */
    public static final byte FLAG_INCOMPLETE = (byte)'+';

    /**
     * End-line flag for aborted message.
     */
    public static final byte FLAG_ABORT = (byte)'#';

    /**
     * Request method SEND.
     */
    public static final String METHOD_SEND = "SEND";

    /**
     * Request method REPORT.
     */
    public static final String METHOD_REPORT = "REPORT";

    public static final int RESPONSE_CODE_OK = 200;
    public static final int RESPONSE_CODE_BAD_REQUEST = 400;
    public static final int RESPONSE_CODE_PROHIBITED = 403;
    public static final int RESPONSE_CODE_TIMEOUT = 408;
    public static final int RESPONSE_CODE_STOP_MESSAGE = 413;
    public static final int RESPONSE_CODE_UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int RESPONSE_CODE_PARAMETER_OUT_OF_BOUNDS = 423;
    public static final int RESPONSE_CODE_SESSION_DOES_NOT_EXIST = 481;
    public static final int RESPONSE_CODE_UNKNOWN_METHOD = 501;
    public static final int RESPONSE_CODE_SESSION_ALREADY_BOUND = 506;

    private MsrpConstants() {
    }
}

