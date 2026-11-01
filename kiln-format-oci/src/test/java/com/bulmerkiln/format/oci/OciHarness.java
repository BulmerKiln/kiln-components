/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.oci;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.oci.constant.OciMediaTypes;
import com.bulmerkiln.format.oci.service.OciAccessService;
import com.bulmerkiln.format.oci.web.OciController;
import com.bulmerkiln.format.oci.web.OciRouteDispatcher;
import com.bulmerkiln.format.oci.web.handler.OciBlobHandler;
import com.bulmerkiln.format.oci.web.handler.OciManifestHandler;
import com.bulmerkiln.format.oci.web.handler.OciTagHandler;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.permissions.access.FormatAccess;
import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.lookup.StoredRoleBindingLookup;
import com.bulmerkiln.permissions.mapper.RoleBindingMapperImpl;
import com.bulmerkiln.permissions.policy.RoleBasedPolicy;
import com.bulmerkiln.permissions.repository.RoleBindingRepository;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.service.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.service.RegistryPolicy;
import com.bulmerkiln.registry.store.NamespaceStore;
import com.bulmerkiln.storage.fs.FsBlobStore;
import com.bulmerkiln.storage.service.SingleStoreRouter;
import com.bulmerkiln.web.FormatRequests;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The OCI routes wired to real services: PostgreSQL with every migration, a filesystem blob store,
 * role bindings in the database, and a caller the test chooses. Namespace {@code acme} (private)
 * owns {@code acme/*}; {@code pub} (public) owns {@code pub/*}.
 */
public final class OciHarness implements AutoCloseable {

    public static final Principal WRITER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ADMIN = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    /** May write to {@code pub} only. */
    static final Principal PUBLISHER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());

    public static final Principal STRANGER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ANONYMOUS = new Principal.Anonymous();

    private final Database database;
    private final Organization acme;
    private final MockMvc mvc;
    private final List<String> events = new ArrayList<>();
    private Principal caller = WRITER;

    public OciHarness(Path blobs) throws IOException {
        this(blobs, UnaryOperator.identity());
    }

    /** {@code addressing} turns a repository path into the one clients use, to test a prefix. */
    public OciHarness(Path blobs, UnaryOperator<String> addressing) throws IOException {
        database = KilnPostgres.database();
        acme = KilnPostgres.organization(database, "acme");
        StoredRoleBindingLookup bindings = new StoredRoleBindingLookup(
                database, KilnPostgres.repository(database, RoleBindingRepository.class), new RoleBindingMapperImpl());
        OrganizationContext.run(acme, () -> {
            database.run(tx -> {
                NamespaceStore.create(tx, namespace("acme", Visibility.PRIVATE, "acme"));
                NamespaceStore.create(tx, namespace("pub", Visibility.PUBLIC, "pub"));
            });
            bindings.add(new RoleBinding(subject(WRITER), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(subject(WRITER), Role.WRITER, Scope.namespace("pub")));
            bindings.add(new RoleBinding(subject(ADMIN), Role.ADMIN, Scope.organization()));
            bindings.add(new RoleBinding(subject(PUBLISHER), Role.WRITER, Scope.namespace("pub")));
        });
        String prefix = addressing.apply("");
        OrganizationResolver organizations = path -> path.startsWith(prefix)
                ? Optional.of(new OrganizationResolver.Resolution(acme, path.substring(prefix.length())))
                : Optional.empty();
        OciAccessService access = new OciAccessService(
                database,
                new FormatAccess(new RoleBasedPolicy(bindings), new NamespaceVisibilityLookup(database)),
                new RegistryPolicy(database));
        ArtifactEventService recorded = new ArtifactEventService(List.of(new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {
                events.add("pushed " + artifact.getName() + " " + artifact.getVersion() + " " + bytes);
            }

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {
                events.add("pulled " + artifact.getName() + " " + artifact.getVersion() + " " + bytes);
            }
        }));
        OciController controller = new OciController(new OciRouteDispatcher(
                new FormatRequests(organizations, request -> caller, "http://localhost"),
                OciManifestHandler.of(database, access, recorded, 4096),
                OciBlobHandler.of(database, access, router(blobs), 64),
                OciTagHandler.of(database, access, 3)));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    public MockMvc mvc() {
        return mvc;
    }

    public Database database() {
        return database;
    }

    public Organization acme() {
        return acme;
    }

    List<String> events() {
        return events;
    }

    /** Makes the next requests as {@code principal}. */
    public void callAs(Principal principal) {
        caller = principal;
    }

    /** Uploads a blob in one request and returns its digest. */
    public Digest blob(String repository, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Digest digest = Digest.sha256(bytes);
        mvc.perform(request(HttpMethod.POST, "/v2/" + repository + "/blobs/uploads/")
                        .param("digest", digest.toString())
                        .content(bytes))
                .andReturn();
        return digest;
    }

    /** An image manifest with this config and these layers, as JSON. */
    public static String image(Digest config, Digest... layers) {
        StringBuilder json = new StringBuilder("{\"schemaVersion\":2,\"mediaType\":\"" + OciMediaTypes.OCI_MANIFEST
                + "\",\"config\":" + descriptor("application/vnd.oci.image.config.v1+json", config, 2)
                + ",\"layers\":[");
        for (int i = 0; i < layers.length; i++) {
            json.append(i == 0 ? "" : ",").append(descriptor("application/vnd.oci.image.layer.v1.tar", layers[i], 5));
        }
        return json.append("]}").toString();
    }

    /** An artifact manifest whose subject is {@code subject}. */
    static String referrer(Digest config, String artifactType, Digest subject) {
        return "{\"schemaVersion\":2,\"mediaType\":\"" + OciMediaTypes.OCI_MANIFEST + "\",\"artifactType\":\""
                + artifactType + "\",\"config\":" + descriptor("application/vnd.oci.empty.v1+json", config, 2)
                + ",\"layers\":[],\"subject\":" + descriptor(OciMediaTypes.OCI_MANIFEST, subject, 10) + "}";
    }

    static String index(Digest... manifests) {
        StringBuilder json = new StringBuilder(
                "{\"schemaVersion\":2,\"mediaType\":\"" + OciMediaTypes.OCI_INDEX + "\",\"manifests\":[");
        for (int i = 0; i < manifests.length; i++) {
            json.append(i == 0 ? "" : ",").append(descriptor(OciMediaTypes.OCI_MANIFEST, manifests[i], 10));
        }
        return json.append("]}").toString();
    }

    /** Pushes a manifest and returns the response. */
    public MvcResult push(String repository, String reference, String json, String mediaType) throws Exception {
        return mvc.perform(request(HttpMethod.PUT, "/v2/" + repository + "/manifests/" + reference)
                        .contentType(mediaType)
                        .content(json))
                .andReturn();
    }

    public static Digest digest(String json) {
        return Digest.sha256(json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        database.close();
    }

    private static SingleStoreRouter router(Path blobs) throws IOException {
        return new SingleStoreRouter(new FsBlobStore(blobs));
    }

    private static String descriptor(String mediaType, Digest digest, long size) {
        return "{\"mediaType\":\"" + mediaType + "\",\"digest\":\"" + digest + "\",\"size\":" + size + "}";
    }

    private static Namespace namespace(String name, Visibility visibility, String prefix) {
        return new Namespace(UUID.randomUUID(), name, visibility, prefix, null, List.of(), List.of(), List.of());
    }

    private static RoleBinding.Subject subject(Principal principal) {
        return new RoleBinding.Subject.Member(((Principal.Member) principal).getId());
    }
}
