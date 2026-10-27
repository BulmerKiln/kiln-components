/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** Pushing, pulling, tagging, listing and deleting through the {@code /v2/} routes. */
class OciPushPullTest {

    @TempDir
    static Path blobs;

    private static OciHarness oci;

    @BeforeAll
    static void start() throws Exception {
        oci = new OciHarness(blobs);
    }

    @AfterAll
    static void stop() {
        oci.close();
    }

    /** FMT-OCI-28: docker's first request, with the API version header. */
    @Test
    void answersThePing() throws Exception {
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Distribution-API-Version", "registry/2.0"))
                .andExpect(content().json("{}"));
    }

    /** FMT-OCI-17: the exact bytes come back by tag and by digest, with their media type and digest. */
    @Test
    void pullsWhatWasPushedByteForByte() throws Exception {
        Digest config = oci.blob("acme/app", "{}");
        Digest layer = oci.blob("acme/app", "layer");
        String manifest = OciHarness.image(config, layer).replace(",", ", ");
        Digest digest = OciHarness.digest(manifest);

        MvcResult pushed = oci.push("acme/app", "1.0", manifest, OciMediaTypes.OCI_MANIFEST);
        assertThat(pushed.getResponse().getStatus()).isEqualTo(201);
        assertThat(pushed.getResponse().getHeader("Location")).isEqualTo("/v2/acme/app/manifests/" + digest);
        assertThat(pushed.getResponse().getHeader("Docker-Content-Digest")).isEqualTo(digest.toString());

        for (String reference : new String[] {"1.0", digest.toString()}) {
            oci.mvc()
                    .perform(request(HttpMethod.GET, "/v2/acme/app/manifests/" + reference))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", OciMediaTypes.OCI_MANIFEST))
                    .andExpect(header().string("Docker-Content-Digest", digest.toString()))
                    .andExpect(content().string(manifest));
        }
        oci.mvc()
                .perform(request(HttpMethod.HEAD, "/v2/acme/app/manifests/1.0"))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", digest.toString()))
                .andExpect(header().longValue("Content-Length", manifest.length()));
    }

    /** FMT-OCI-21: blobs come back whole or by range, with their digest. */
    @Test
    void pullsBlobsWholeAndByRange() throws Exception {
        Digest layer = oci.blob("acme/ranges", "0123456789");

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/ranges/blobs/" + layer))
                .andExpect(status().isOk())
                .andExpect(header().string("Docker-Content-Digest", layer.toString()))
                .andExpect(content().string("0123456789"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/ranges/blobs/" + layer)
                        .header("Range", "bytes=2-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-4/10"))
                .andExpect(content().string("234"));
        oci.mvc()
                .perform(request(HttpMethod.HEAD, "/v2/acme/ranges/blobs/" + layer))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", 10));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/ranges/blobs/" + layer)
                        .header("Range", "bytes=20-30"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    /** FMT-OCI-21: a blob another repository holds is unknown here. */
    @Test
    void keepsBlobsToTheRepositoriesHoldingThem() throws Exception {
        Digest secret = oci.blob("acme/one", "secret");

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/two/blobs/" + secret))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UNKNOWN"));
    }

    /** FMT-OCI-18: everything a manifest references must already be in the repository. */
    @Test
    void refusesManifestsReferencingUnknownContent() throws Exception {
        Digest config = oci.blob("acme/refs", "{}");
        Digest missing = Digest.sha256(new byte[] {1});

        MvcResult image = oci.push("acme/refs", "1", OciHarness.image(config, missing), OciMediaTypes.OCI_MANIFEST);
        MvcResult index = oci.push("acme/refs", "2", OciHarness.index(missing), OciMediaTypes.OCI_INDEX);

        assertThat(image.getResponse().getStatus()).isEqualTo(400);
        assertThat(image.getResponse().getContentAsString()).contains("MANIFEST_BLOB_UNKNOWN");
        assertThat(index.getResponse().getContentAsString()).contains("MANIFEST_BLOB_UNKNOWN");
    }

    /** FMT-OCI-19: a push by digest must match its own content. */
    @Test
    void refusesADigestThatIsNotTheContents() throws Exception {
        Digest config = oci.blob("acme/bydigest", "{}");
        String manifest = OciHarness.image(config);

        MvcResult wrong = oci.push(
                "acme/bydigest", Digest.sha256(new byte[] {2}).toString(), manifest, OciMediaTypes.OCI_MANIFEST);
        MvcResult right =
                oci.push("acme/bydigest", OciHarness.digest(manifest).toString(), manifest, OciMediaTypes.OCI_MANIFEST);

        assertThat(wrong.getResponse().getContentAsString()).contains("DIGEST_INVALID");
        assertThat(right.getResponse().getStatus()).isEqualTo(201);
    }

    /** FMT-OCI-20: tags and manifests can be deleted, but not a manifest an index still needs. */
    @Test
    void deletesTagsAndManifests() throws Exception {
        oci.callAs(OciHarness.ADMIN);
        try {
            Digest config = oci.blob("acme/del", "{}");
            String image = OciHarness.image(config);
            Digest imageDigest = OciHarness.digest(image);
            oci.push("acme/del", "keep", image, OciMediaTypes.OCI_MANIFEST);
            oci.push("acme/del", "gone", image, OciMediaTypes.OCI_MANIFEST);
            String index = OciHarness.index(imageDigest);
            oci.push("acme/del", "multi", index, OciMediaTypes.OCI_INDEX);

            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/del/manifests/gone"))
                    .andExpect(status().isAccepted());
            oci.mvc()
                    .perform(request(HttpMethod.GET, "/v2/acme/del/manifests/gone"))
                    .andExpect(jsonPath("$.errors[0].code").value("MANIFEST_UNKNOWN"));
            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/del/manifests/" + imageDigest))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED"));
            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/del/manifests/" + OciHarness.digest(index)))
                    .andExpect(status().isAccepted());
            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/del/manifests/" + imageDigest))
                    .andExpect(status().isAccepted());
            oci.mvc()
                    .perform(request(HttpMethod.GET, "/v2/acme/del/manifests/keep"))
                    .andExpect(status().isNotFound());
            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/del/manifests/keep"))
                    .andExpect(jsonPath("$.errors[0].code").value("MANIFEST_UNKNOWN"));
            oci.mvc()
                    .perform(request(HttpMethod.DELETE, "/v2/acme/never/manifests/keep"))
                    .andExpect(status().isNotFound());
        } finally {
            oci.callAs(OciHarness.WRITER);
        }
    }

    /** FMT-OCI-26: tags list in lexical order, paged with n, last and a Link header. */
    @Test
    void listsTagsInPages() throws Exception {
        Digest config = oci.blob("acme/tags", "{}");
        String image = OciHarness.image(config);
        for (String tag : new String[] {"b", "a", "d", "c"}) {
            oci.push("acme/tags", tag, image, OciMediaTypes.OCI_MANIFEST);
        }

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/tags/tags/list").param("n", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"name\":\"acme/tags\",\"tags\":[\"a\",\"b\"]}"))
                .andExpect(header().string("Link", "</v2/acme/tags/tags/list?n=2&last=b>; rel=\"next\""));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/tags/tags/list").param("last", "b"))
                .andExpect(content().json("{\"tags\":[\"c\",\"d\"]}"))
                .andExpect(header().doesNotExist("Link"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/tags/tags/list").param("n", "99"))
                .andExpect(jsonPath("$.tags.length()").value(3));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/notags/tags/list"))
                .andExpect(jsonPath("$.errors[0].code").value("NAME_UNKNOWN"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/tags/tags/list").param("n", "x"))
                .andExpect(status().isBadRequest());
    }

    /** FMT-OCI-27: referrers by subject, filterable by artifact type, and OCI-Subject on push. */
    @Test
    void listsReferrers() throws Exception {
        Digest config = oci.blob("acme/sig", "{}");
        String image = OciHarness.image(config);
        Digest subject = OciHarness.digest(image);
        oci.push("acme/sig", "1", image, OciMediaTypes.OCI_MANIFEST);
        String signature = OciHarness.referrer(config, "application/vnd.dev.cosign", subject);
        String sbom = OciHarness.referrer(config, "application/spdx+json", subject);

        MvcResult pushed =
                oci.push("acme/sig", OciHarness.digest(signature).toString(), signature, OciMediaTypes.OCI_MANIFEST);
        oci.push("acme/sig", OciHarness.digest(sbom).toString(), sbom, OciMediaTypes.OCI_MANIFEST);

        assertThat(pushed.getResponse().getHeader("OCI-Subject")).isEqualTo(subject.toString());
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/sig/referrers/" + subject))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", OciMediaTypes.OCI_INDEX))
                .andExpect(jsonPath("$.manifests.length()").value(2))
                .andExpect(header().doesNotExist("OCI-Filters-Applied"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/sig/referrers/" + subject)
                        .param("artifactType", "application/spdx+json"))
                .andExpect(jsonPath("$.manifests.length()").value(1))
                .andExpect(jsonPath("$.manifests[0].artifactType").value("application/spdx+json"))
                .andExpect(header().string("OCI-Filters-Applied", "artifactType"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/nosig/referrers/" + subject))
                .andExpect(jsonPath("$.manifests.length()").value(0));
    }

    /** FMT-OCI-14: routes are read from the end; nonsense is not found, bad names are invalid. */
    @Test
    void answersMalformedRoutesInTheSpecsTerms() throws Exception {
        oci.mvc().perform(request(HttpMethod.GET, "/v2/acme/app/nothing")).andExpect(status().isNotFound());
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/App/manifests/1"))
                .andExpect(jsonPath("$.errors[0].code").value("NAME_INVALID"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/app/blobs/sha256:short"))
                .andExpect(jsonPath("$.errors[0].code").value("DIGEST_INVALID"));
        oci.mvc()
                .perform(request(HttpMethod.DELETE, "/v2/acme/app/blobs/" + Digest.sha256(new byte[0])))
                .andExpect(status().isMethodNotAllowed());
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/app/manifests/1"))
                .andExpect(status().isMethodNotAllowed());
        oci.mvc().perform(request(HttpMethod.PUT, "/v2/acme/app/tags/list")).andExpect(status().isMethodNotAllowed());
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/app/referrers/" + Digest.sha256(new byte[0])))
                .andExpect(status().isMethodNotAllowed());
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/app/blobs/uploads/"))
                .andExpect(status().isMethodNotAllowed());
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/app/blobs/uploads/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed());
    }

    /** Pushes and manifest pulls reach the event listeners; HEAD and blob reads don't count as pulls. */
    @Test
    void reportsPushesAndPulls() throws Exception {
        Digest config = oci.blob("acme/events", "{}");
        String image = OciHarness.image(config);
        oci.events().clear();

        oci.push("acme/events", "1", image, OciMediaTypes.OCI_MANIFEST);
        oci.mvc().perform(request(HttpMethod.GET, "/v2/acme/events/manifests/1"));
        oci.mvc().perform(request(HttpMethod.HEAD, "/v2/acme/events/manifests/1"));

        assertThat(oci.events())
                .containsExactly("pushed acme/events 1 " + image.length(), "pulled acme/events 1 " + image.length());
    }

    @Test
    void theOciSchemaIsOrganizationSafe() {
        assertThat(oci.database().instanceTransaction(SchemaLinter::problems)).isEmpty();
    }
}
