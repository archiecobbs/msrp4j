
/*
 * Copyright (C) 2014 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.msrp.msg;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dellroad.msrp.MsrpConstants;
import org.dellroad.msrp.MsrpUri;

/**
 * MSRP headers.
 */
public class MsrpHeaders {

    private final ArrayList<MsrpUri> toPath = new ArrayList<>();
    private final ArrayList<MsrpUri> fromPath = new ArrayList<>();
    private String messageId;
    private boolean successReport;
    private FailureReport failureReport = FailureReport.YES;
    private ByteRange byteRange;
    private Status status;
    private String contentType;
    private final TreeSet<Header> extensionHeaders = new TreeSet<>(Header.SORT_BY_NAME);
    private final TreeSet<Header> mimeHeaders = new TreeSet<>(Header.SORT_BY_NAME);

    public List<MsrpUri> getToPath() {
        return this.toPath;
    }

    public List<MsrpUri> getFromPath() {
        return this.fromPath;
    }

    public String getMessageId() {
        return this.messageId;
    }
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public boolean isSuccessReport() {
        return this.successReport;
    }
    public void setSuccessReport(boolean successReport) {
        this.successReport = successReport;
    }

    public FailureReport getFailureReport() {
        return this.failureReport;
    }
    public void setFailureReport(FailureReport failureReport) {
        this.failureReport = failureReport;
    }

    public ByteRange getByteRange() {
        return this.byteRange;
    }
    public void setByteRange(ByteRange byteRange) {
        this.byteRange = byteRange;
    }

    public Status getStatus() {
        return this.status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }

    public String getContentType() {
        return this.contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * MIME headers, sorted by header name case-insensitively.
     */
    public SortedSet<Header> getMimeHeaders() {
        return this.mimeHeaders;
    }

    /**
     * Extension headers, sorted by header name case-insensitively.
     */
    public SortedSet<Header> getExtensionHeaders() {
        return this.extensionHeaders;
    }

// Object

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final MsrpHeaders that = (MsrpHeaders)obj;
        if (!this.toPath.equals(that.toPath))
            return false;
        if (!this.fromPath.equals(that.fromPath))
            return false;
        if (!(this.messageId != null ? this.messageId.equals(that.messageId) : that.messageId == null))
            return false;
        if (this.successReport != that.successReport)
            return false;
        if (this.failureReport != that.failureReport)
            return false;
        if (!(this.byteRange != null ? this.byteRange.equals(that.byteRange) : that.byteRange == null))
            return false;
        if (!(this.status != null ? this.status.equals(that.status) : that.status == null))
            return false;
        if (!(this.contentType != null ? this.contentType.equals(that.contentType) : that.contentType == null))
            return false;
        if (!this.extensionHeaders.equals(that.extensionHeaders))
            return false;
        if (!this.mimeHeaders.equals(that.mimeHeaders))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return this.toPath.hashCode()
          ^ this.fromPath.hashCode()
          ^ (this.messageId != null ? this.messageId.hashCode() : 0)
          ^ (this.successReport ? 1 : 0)
          ^ (this.failureReport != null ?  this.failureReport.hashCode() : 0)
          ^ (this.byteRange != null ? this.byteRange.hashCode() : 0)
          ^ (this.status != null ? this.status.hashCode() : 0)
          ^ (this.contentType != null ? this.contentType.hashCode() : 0)
          ^ this.extensionHeaders.hashCode()
          ^ this.mimeHeaders.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        if (!this.toPath.isEmpty()) {
            buf.append(MsrpConstants.TO_PATH_HEADER).append(": ");
            for (MsrpUri uri : this.toPath)
                buf.append(uri);
            buf.append(Util.CRLF);
        }
        if (!this.toPath.isEmpty()) {
            buf.append(MsrpConstants.FROM_PATH_HEADER).append(": ");
            for (MsrpUri uri : this.fromPath)
                buf.append(uri);
            buf.append(Util.CRLF);
        }
        if (this.messageId != null)
            buf.append(MsrpConstants.MESSAGE_ID_HEADER).append(": ").append(this.messageId).append(Util.CRLF);
        if (this.successReport)
            buf.append(MsrpConstants.SUCCESS_REPORT_HEADER).append(": ").append(MsrpConstants.YES_HEADER_VALUE).append(Util.CRLF);
        if (this.failureReport != null && this.failureReport != FailureReport.YES)
            buf.append(MsrpConstants.FAILURE_REPORT_HEADER).append(": ").append(this.failureReport).append(Util.CRLF);
        if (this.byteRange != null)
            buf.append(MsrpConstants.BYTE_RANGE_HEADER).append(": ").append(this.byteRange).append(Util.CRLF);
        if (this.status != null)
            buf.append(MsrpConstants.STATUS_HEADER).append(": ").append(this.status).append(Util.CRLF);
        for (Header header : this.extensionHeaders)
            buf.append(header).append(Util.CRLF);
        for (Header header : this.mimeHeaders)
            buf.append(header).append(Util.CRLF);
        if (this.contentType != null)
            buf.append(MsrpConstants.CONTENT_TYPE_HEADER).append(": ").append(this.contentType).append(Util.CRLF);
        return buf.toString();
    }
}

