/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.s3;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the S3 store.
 *
 * @param endpoint for S3-compatible stores such as SeaweedFS or Ceph RGW; AWS when unset
 * @param pathStyle path-style addressing, which most S3-compatible stores need
 * @param prefix a key prefix within the bucket
 * @param redirect when downloads redirect to presigned URLs; {@code auto} by default
 * @param presignTtl how long a presigned URL lives: at most 60 s, the only window in which a
 *     blocked artifact can still be fetched
 * @param allowInsecureEndpoint allows an {@code http} endpoint, for a store on the same host
 */
@ConfigurationProperties("kiln.storage.s3")
public record S3StorageProperties(
        @Nullable String bucket,
        @Nullable String region,
        @Nullable URI endpoint,
        boolean pathStyle,
        @Nullable String prefix,
        @Nullable RedirectMode redirect,
        @Nullable Duration presignTtl,
        boolean allowInsecureEndpoint) {

    public static final Duration MAX_PRESIGN_TTL = Duration.ofSeconds(60);

    /** S3 bucket naming rules, minus the dotted names that break virtual-hosted TLS. */
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]");

    /** Validates the settings, naming the setting at fault. */
    public void validate() {
        validateBucket();
        validateEndpoint();
        validatePresignTtl();
        keys();
    }

    private void validateBucket() {
        if (bucket == null
                || !BUCKET.matcher(bucket).matches()
                || bucket.startsWith("xn--")
                || bucket.endsWith("-s3alias")) {
            throw new IllegalArgumentException("kiln.storage.s3.bucket must be a valid S3 bucket name");
        }
    }

    private void validateEndpoint() {
        if (endpoint == null) {
            return;
        }
        String scheme = endpoint.getScheme();
        boolean secure = "https".equals(scheme) || (allowInsecureEndpoint && "http".equals(scheme));
        if (!secure || endpoint.getHost() == null || endpoint.getRawUserInfo() != null) {
            throw new IllegalArgumentException("kiln.storage.s3.endpoint must be an https URL without credentials");
        }
    }

    private void validatePresignTtl() {
        Duration ttl = presignTtlOrDefault();
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_PRESIGN_TTL) > 0) {
            throw new IllegalArgumentException("kiln.storage.s3.presign-ttl must be between 1 and 60 seconds");
        }
    }

    public Duration presignTtlOrDefault() {
        return presignTtl == null ? MAX_PRESIGN_TTL : presignTtl;
    }

    public RedirectPolicy redirectPolicy() {
        return new RedirectPolicy(redirect == null ? RedirectMode.AUTO : redirect);
    }

    public S3Keys keys() {
        return new S3Keys(prefix == null ? "" : prefix);
    }
}
