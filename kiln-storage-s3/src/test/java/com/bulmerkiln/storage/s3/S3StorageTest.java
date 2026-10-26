/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.org.OrgId;
import com.bulmerkiln.storage.UploadId;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class S3StorageTest {

    private static final OrgId ORG = OrgId.of("7d6f5a2e-1c3b-4a5d-9e8f-0a1b2c3d4e5f");
    private static final Digest EMPTY = Digest.sha256(new byte[0]);

    @Test
    void laysOutKeysLikeTheFilesystemStore() {
        assertThat(new S3Keys("").blob(ORG, EMPTY)).isEqualTo(ORG + "/blobs/sha256/e3/" + EMPTY.encoded());
        assertThat(new S3Keys("kiln/prod").blob(ORG, EMPTY)).startsWith("kiln/prod/" + ORG + "/blobs/");
        UploadId upload = UploadId.random();
        assertThat(new S3Keys("").upload(ORG, upload)).isEqualTo(ORG + "/_uploads/" + upload);
        assertThat(new S3Keys("kiln").org(ORG)).isEqualTo("kiln/" + ORG + "/");
    }

    @Test
    void organizationsNeverShareAKey() {
        OrgId other = new OrgId(UUID.randomUUID());
        S3Keys keys = new S3Keys("");
        assertThat(keys.blob(ORG, EMPTY)).isNotEqualTo(keys.blob(other, EMPTY));
        assertThat(keys.blob(other, EMPTY)).doesNotStartWith(keys.org(ORG));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/kiln", "kiln/", "kiln//prod", "../kiln", "kiln/../x", "Kiln", "kiln prod", "kiln/.x", "-kiln"})
    void refusesUnsafePrefixes(String prefix) {
        assertThatIllegalArgumentException().isThrownBy(() -> new S3Keys(prefix));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "docker/27.1.1 go/go1.22.5 git-commit/abc kernel/6.6 os/linux arch/amd64",
                "containerd/1.7.20",
                "buildkit/v0.15",
                "containers/5.32.0 (github.com/containers/image)",
                "Helm/3.15.3",
                "npm/10.8.1 node/v20.15.0 linux x64 workspaces/false",
                "Apache-Maven/3.9.8 (Java 21; Linux 6.6)",
                "Gradle/8.9 (Linux;6.6;amd64) (Eclipse Adoptium;21;21+35)",
                "oras/1.2.0"
            })
    void autoRedirectsKnownGoodClients(String userAgent) {
        assertThat(new RedirectPolicy(RedirectMode.AUTO).shouldRedirect(userAgent))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "curl/8.5.0",
                "python-requests/2.32",
                "Mozilla/5.0",
                "dockerx/1.0",
                "docker",
                "Docker/27",
                " docker/27",
                "evil docker/27",
                "npm",
                "/docker",
                "",
                "Go-http-client/1.1",
                "helm/3.15.3"
            })
    void autoProxiesEveryoneElse(String userAgent) {
        assertThat(new RedirectPolicy(RedirectMode.AUTO).shouldRedirect(userAgent))
                .isFalse();
        assertThat(new RedirectPolicy(RedirectMode.AUTO).shouldRedirect(null)).isFalse();
    }

    @Test
    void alwaysAndNeverOverrideTheAllowlist() {
        assertThat(new RedirectPolicy(RedirectMode.ALWAYS).shouldRedirect("curl/8"))
                .isTrue();
        assertThat(new RedirectPolicy(RedirectMode.NEVER).shouldRedirect("docker/27"))
                .isFalse();
    }

    @Test
    void validatesSettingsWithSafeDefaults() {
        S3StorageProperties properties = properties("kiln-blobs", null, null);
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.presignTtlOrDefault()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.redirectPolicy().mode()).isEqualTo(RedirectMode.AUTO);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ab",
                "Kiln",
                "kiln_blobs",
                "kiln.blobs",
                "-kiln",
                "kiln-",
                "xn--kiln",
                "kiln-s3alias",
                "192.168.1.1"
            })
    void refusesInvalidBuckets(String bucket) {
        assertThatIllegalArgumentException()
                .isThrownBy(properties(bucket, null, null)::validate)
                .withMessageContaining("bucket");
    }

    @Test
    void requiresABucket() {
        assertThatIllegalArgumentException().isThrownBy(properties(null, null, null)::validate);
    }

    @Test
    void capsThePresignedUrlLifetime() {
        assertThatCode(properties("kiln-blobs", null, Duration.ofSeconds(1))::validate)
                .doesNotThrowAnyException();
        for (Duration ttl :
                new Duration[] {Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(61), Duration.ofHours(1)}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(properties("kiln-blobs", null, ttl)::validate)
                    .withMessageContaining("presign-ttl");
        }
    }

    @Test
    void endpointsMustBeHttpsWithoutCredentials() {
        assertThatCode(properties("kiln-blobs", URI.create("https://s3.acme.internal"), null)::validate)
                .doesNotThrowAnyException();
        for (String endpoint : new String[] {
            "http://s3.acme.internal", "https://key:secret@s3.acme.internal", "file:///tmp", "s3.acme.internal"
        }) {
            assertThatIllegalArgumentException()
                    .isThrownBy(properties("kiln-blobs", URI.create(endpoint), null)::validate)
                    .withMessageNotContaining("secret");
        }
        S3StorageProperties local = new S3StorageProperties(
                "kiln-blobs", null, URI.create("http://seaweedfs:8333"), true, null, null, null, true);
        assertThatCode(local::validate).doesNotThrowAnyException();
    }

    private static S3StorageProperties properties(String bucket, URI endpoint, Duration ttl) {
        return new S3StorageProperties(bucket, "eu-west-1", endpoint, false, null, null, ttl, false);
    }
}
