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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** FMT-OCI-22 to FMT-OCI-25: every way to upload a blob, and every way one goes wrong. */
class OciUploadTest {

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

    /** FMT-OCI-22, FMT-OCI-24: a session, chunks in order, its status, and the final PUT. */
    @Test
    void uploadsInChunks() throws Exception {
        String session = startSession("acme/chunks");
        Digest digest = Digest.sha256("0123456789".getBytes(StandardCharsets.UTF_8));

        oci.mvc()
                .perform(request(HttpMethod.PATCH, session)
                        .header("Content-Range", "0-4")
                        .content("01234"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Range", "0-4"))
                .andExpect(header().string("Location", "http://localhost" + session));
        oci.mvc()
                .perform(request(HttpMethod.PATCH, session).content("56789"))
                .andExpect(header().string("Range", "0-9"));
        oci.mvc()
                .perform(request(HttpMethod.GET, session))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Range", "0-9"));
        oci.mvc()
                .perform(request(HttpMethod.PUT, session).param("digest", digest.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v2/acme/chunks/blobs/" + digest))
                .andExpect(header().string("Docker-Content-Digest", digest.toString()));

        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/chunks/blobs/" + digest))
                .andExpect(content().string("0123456789"));
        oci.mvc().perform(request(HttpMethod.GET, session)).andExpect(status().isNotFound());
    }

    /** FMT-OCI-22: a session finished with its last chunk in the PUT. */
    @Test
    void uploadsMonolithically() throws Exception {
        String session = startSession("acme/mono");
        Digest digest = Digest.sha256("whole".getBytes(StandardCharsets.UTF_8));

        oci.mvc()
                .perform(request(HttpMethod.PUT, session)
                        .param("digest", digest.toString())
                        .content("whole"))
                .andExpect(status().isCreated());
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/mono/blobs/" + digest))
                .andExpect(content().string("whole"));
    }

    /** FMT-OCI-24: a gap or overlap is 416, and the session stays as it was. */
    @Test
    void refusesChunksOutOfOrder() throws Exception {
        String session = startSession("acme/gaps");
        oci.mvc()
                .perform(request(HttpMethod.PATCH, session)
                        .header("Content-Range", "0-2")
                        .content("abc"));

        oci.mvc()
                .perform(request(HttpMethod.PATCH, session)
                        .header("Content-Range", "5-7")
                        .content("fgh"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        oci.mvc()
                .perform(request(HttpMethod.PATCH, session)
                        .header("Content-Range", "1-3")
                        .content("bcd"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        oci.mvc()
                .perform(request(HttpMethod.PATCH, session)
                        .header("Content-Range", "3-1")
                        .content("x"))
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UPLOAD_INVALID"));
        oci.mvc().perform(request(HttpMethod.GET, session)).andExpect(header().string("Range", "0-2"));
    }

    /** FMT-OCI-25: content that doesn't match its digest is refused, and the session ends. */
    @Test
    void refusesTheWrongDigest() throws Exception {
        String session = startSession("acme/wrong");

        oci.mvc()
                .perform(request(HttpMethod.PUT, session)
                        .param("digest", Digest.sha256(new byte[] {9}).toString())
                        .content("not nine"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("DIGEST_INVALID"));
        oci.mvc().perform(request(HttpMethod.GET, session)).andExpect(status().isNotFound());
        oci.mvc()
                .perform(request(HttpMethod.PUT, startSession("acme/wrong")))
                .andExpect(jsonPath("$.errors[0].code").value("DIGEST_INVALID"));
    }

    /** FMT-OCI-24: blobs past the limit are refused as too large. */
    @Test
    void refusesBlobsPastTheLimit() throws Exception {
        String session = startSession("acme/big");

        oci.mvc()
                .perform(request(HttpMethod.PATCH, session).content(new byte[65]))
                .andExpect(status().is(413));
    }

    /** FMT-OCI-25: a session belongs to the repository that started it, and IDs are strict. */
    @Test
    void keepsSessionsToTheirRepository() throws Exception {
        String session = startSession("acme/mine");
        String elsewhere = session.replace("/acme/mine/", "/acme/yours/");

        oci.mvc()
                .perform(request(HttpMethod.PATCH, elsewhere).content("x"))
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UPLOAD_UNKNOWN"));
        oci.mvc()
                .perform(request(HttpMethod.PATCH, "/v2/acme/mine/blobs/uploads/NOT-A-UUID")
                        .content("x"))
                .andExpect(jsonPath("$.errors[0].code").value("BLOB_UPLOAD_UNKNOWN"));
        oci.mvc().perform(request(HttpMethod.DELETE, session)).andExpect(status().isNoContent());
        oci.mvc().perform(request(HttpMethod.GET, session)).andExpect(status().isNotFound());
    }

    /** FMT-OCI-23: a mount links a blob the caller can read elsewhere; anything else is an upload. */
    @Test
    void mountsOnlyWhatTheCallerCanRead() throws Exception {
        Digest shared = oci.blob("acme/source", "shared");

        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/target/blobs/uploads/")
                        .param("mount", shared.toString())
                        .param("from", "acme/source"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v2/acme/target/blobs/" + shared));
        oci.mvc()
                .perform(request(HttpMethod.GET, "/v2/acme/target/blobs/" + shared))
                .andExpect(content().string("shared"));

        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/target/blobs/uploads/")
                        .param("mount", Digest.sha256(new byte[] {3}).toString())
                        .param("from", "acme/source"))
                .andExpect(status().isAccepted());
        oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/acme/target/blobs/uploads/")
                        .param("mount", shared.toString())
                        .param("from", "Not A Name"))
                .andExpect(status().isAccepted());
        oci.callAs(OciHarness.PUBLISHER);
        try {
            oci.mvc()
                    .perform(request(HttpMethod.POST, "/v2/pub/target/blobs/uploads/")
                            .param("mount", shared.toString())
                            .param("from", "acme/source"))
                    .andExpect(status().isAccepted());
            oci.mvc()
                    .perform(request(HttpMethod.GET, "/v2/pub/target/blobs/" + shared))
                    .andExpect(status().isNotFound());
        } finally {
            oci.callAs(OciHarness.WRITER);
        }
    }

    private static String startSession(String repository) throws Exception {
        MvcResult started = oci.mvc()
                .perform(request(HttpMethod.POST, "/v2/" + repository + "/blobs/uploads/"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Range", "0-0"))
                .andExpect(header().exists("Docker-Upload-UUID"))
                .andReturn();
        String location = started.getResponse().getHeader("Location");
        assertThat(location).startsWith("http://localhost/v2/" + repository + "/blobs/uploads/");
        return location.substring("http://localhost".length());
    }
}
