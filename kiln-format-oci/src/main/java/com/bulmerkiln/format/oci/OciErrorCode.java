/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import com.bulmerkiln.format.Reason;
import com.bulmerkiln.format.Refusal;

/**
 * The distribution-spec error codes (spec.md, "Error codes"). Each has a {@link Reason}, which
 * fixes its HTTP status to the one distribution/distribution answers with.
 */
public enum OciErrorCode {
    BLOB_UNKNOWN(Reason.NOT_FOUND, "blob unknown to registry"),
    BLOB_UPLOAD_INVALID(Reason.INVALID, "blob upload invalid"),
    BLOB_UPLOAD_UNKNOWN(Reason.NOT_FOUND, "blob upload unknown to registry"),
    DIGEST_INVALID(Reason.INVALID, "provided digest did not match uploaded content"),
    MANIFEST_BLOB_UNKNOWN(Reason.INVALID, "manifest references a manifest or blob unknown to registry"),
    MANIFEST_INVALID(Reason.INVALID, "manifest invalid"),
    MANIFEST_UNKNOWN(Reason.NOT_FOUND, "manifest unknown to registry"),
    NAME_INVALID(Reason.INVALID, "invalid repository name"),
    NAME_UNKNOWN(Reason.NOT_FOUND, "repository name not known to registry"),
    SIZE_INVALID(Reason.INVALID, "provided length did not match content length"),
    UNAUTHORIZED(Reason.UNAUTHORIZED, "authentication required"),
    DENIED(Reason.DENIED, "requested access to the resource is denied"),
    UNSUPPORTED(Reason.UNSUPPORTED, "the operation is unsupported"),
    TOOMANYREQUESTS(Reason.RATE_LIMITED, "too many requests");

    private final Reason reason;
    private final String message;

    OciErrorCode(Reason reason, String message) {
        this.reason = reason;
        this.message = message;
    }

    public Reason reason() {
        return reason;
    }

    public int status() {
        return reason.status();
    }

    /**
     * The code to answer a refusal with: its hint when that names a code with the same reason,
     * otherwise the code for the reason. A hint can't change what the client sees as the outcome,
     * so a block can never go out as, say, {@code BLOB_UNKNOWN}.
     */
    public static OciErrorCode forRefusal(Refusal refusal) {
        String hint = refusal.code();
        if (hint != null) {
            for (OciErrorCode code : values()) {
                if (code.name().equals(hint) && code.reason == refusal.reason()) {
                    return code;
                }
            }
        }
        return switch (refusal.reason()) {
            case INVALID -> MANIFEST_INVALID;
            case UNAUTHORIZED -> UNAUTHORIZED;
            case DENIED, BLOCKED, OVER_QUOTA, CONFLICT -> DENIED;
            case NOT_FOUND -> NAME_UNKNOWN;
            case UNSUPPORTED -> UNSUPPORTED;
            case TOO_LARGE -> SIZE_INVALID;
            case RATE_LIMITED -> TOOMANYREQUESTS;
        };
    }

    /** The spec's generic message, used when there's nothing more specific to say. */
    public String message() {
        return message;
    }
}
