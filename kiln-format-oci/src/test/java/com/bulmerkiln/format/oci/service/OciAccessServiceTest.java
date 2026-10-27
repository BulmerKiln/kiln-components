/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.format.oci.OciFormatModule;
import com.bulmerkiln.format.oci.OciHarness;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.registry.store.BlockStore;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

/** FMT-OCI-15, FMT-OCI-16: who may do what, the challenge docker follows, and policy. */
class OciAccessServiceTest {

    @TempDir
    static Path blobs;

    private static OciHarness oci;
    private static String image;

    @BeforeAll
    static void start() throws Exception {
        oci = new OciHarness(blobs);
        Digest config = oci.blob("acme/private", "{}");
        image = OciHarness.image(config);
        oci.push("acme/private", "1", image, OciMediaTypes.OCI_MANIFEST);
        Digest publicConfig = oci.blob("pub/open", "{}");
        oci.push("pub/open", "1", OciHarness.image(publicConfig), OciMediaTypes.OCI_MANIFEST);
    }

    @AfterAll
    static void stop() {
        oci.close();
    }

    @AfterEach
    void asTheWriter() {
        oci.callAs(OciHarness.WRITER);
    }

    /** Anyone not signed in is asked to, with the realm and scope docker needs. */
    @Test
    void challengesAnonymousCallers() throws Exception {
        oci.callAs(OciHarness.ANONYMOUS);

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/private/manifests/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                                "WWW-Authenticate",
                                "Bearer realm=\"http://localhost/token\",service=\"localhost\","
                                        + "scope=\"repository:acme/private:pull\""))
                .andExpect(jsonPath("$.errors[0].code").value("UNAUTHORIZED"));
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/private/blobs/uploads/"))
                .andExpect(header().string(
                                "WWW-Authenticate",
                                "Bearer realm=\"http://localhost/token\",service=\"localhost\","
                                        + "scope=\"repository:acme/private:pull,push\""));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/missing/manifests/1"))
                .andExpect(status().isUnauthorized());
    }

    /** Public namespaces can be pulled by anyone, and nothing more. */
    @Test
    void letsAnyonePullPublicContent() throws Exception {
        oci.callAs(OciHarness.ANONYMOUS);

        oci.mvc().perform(request(HttpMethod.GET, "/v2/pub/open/manifests/1")).andExpect(status().isOk());
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/pub/open/blobs/uploads/"))
                .andExpect(status().isUnauthorized());
    }

    /** A member who may not know a repository exists can't tell it from a missing one. */
    @Test
    void hidesPrivateContentFromOtherMembers() throws Exception {
        oci.callAs(OciHarness.STRANGER);

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/private/manifests/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("NAME_UNKNOWN"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/missing/manifests/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("NAME_UNKNOWN"));
    }

    /** Writers push but don't delete. */
    @Test
    void keepsDeletesToThoseWhoMay() throws Exception {
        oci.mvc()
                .perform(request(HttpMethod.DELETE, "/v2/acme/private/manifests/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("DENIED"));
    }

    /** A name no namespace owns can't be pushed to, even by an admin. */
    @Test
    void refusesPushesOutsideEveryNamespace() throws Exception {
        oci.callAs(OciHarness.ADMIN);

        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/nobody/app/blobs/uploads/"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("No namespace owns nobody/app")));
    }

    /** A block is 403, never 404, by tag or by digest; a tombstoned tag can't be pushed again. */
    @Test
    void refusesBlockedAndTombstonedContent() throws Exception {
        Digest digest = OciHarness.digest(image);
        oci.push("acme/private", "2", image, OciMediaTypes.OCI_MANIFEST);
        OrganizationContext.run(
                oci.acme(),
                () -> oci.database().run(tx -> {
                    BlockStore.block(tx, OciFormatModule.FORMAT, "acme/private", digest.toString(), "CVE-2026-0001");
                    BlockStore.tombstone(tx, OciFormatModule.FORMAT, "acme/private", "3");
                }));

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/private/manifests/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("DENIED"))
                .andExpect(
                        jsonPath("$.errors[0].message").value("acme/private " + digest + " is blocked: CVE-2026-0001"));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/private/manifests/" + digest))
                .andExpect(status().isForbidden());
        Digest config = oci.blob("acme/private", "{}");
        oci.mvc()
                .perform(request(HttpMethod.PUT, "/v2/acme/private/manifests/3")
                        .contentType(OciMediaTypes.OCI_MANIFEST)
                        .content(OciHarness.image(config, config)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].message")
                        .value("acme/private 3 was deleted and can't be published again"));
    }

    /** With organizations addressed by path, URLs and challenges keep the prefix; storage doesn't. */
    @Test
    void keepsTheOrganizationPrefixInWhatClientsSee(@TempDir Path other) throws Exception {
        try (OciHarness prefixed = new OciHarness(other, path -> "acme/" + path)) {
            prefixed.mvc()
                    .perform(request(HttpMethod.POST, "/v2/acme/acme/app/blobs/uploads/"))
                    .andExpect(status().isAccepted())
                    .andExpect(header().string(
                                    "Location", startsWith("http://localhost/v2/acme/acme/app/blobs/uploads/")));
            prefixed.callAs(OciHarness.ANONYMOUS);
            prefixed.mvc()
                    .perform(request(HttpMethod.GET, "/v2/acme/acme/app/tags/list"))
                    .andExpect(header().string("WWW-Authenticate", containsString("repository:acme/acme/app:pull")));
            prefixed.mvc()
                    .perform(request(HttpMethod.GET, "/v2/globex/acme/app/tags/list"))
                    .andExpect(status().isNotFound());
        }
    }
}
