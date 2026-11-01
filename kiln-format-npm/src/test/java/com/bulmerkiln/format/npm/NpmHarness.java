/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.npm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.npm.service.NpmAccessService;
import com.bulmerkiln.format.npm.service.NpmPublishService;
import com.bulmerkiln.format.npm.service.NpmReadService;
import com.bulmerkiln.format.npm.service.NpmTagService;
import com.bulmerkiln.format.npm.service.NpmUnpublishService;
import com.bulmerkiln.format.npm.web.NpmController;
import com.bulmerkiln.format.npm.web.NpmRouteDispatcher;
import com.bulmerkiln.format.npm.web.handler.NpmPackageHandler;
import com.bulmerkiln.format.npm.web.handler.NpmUnpublishHandler;
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
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.fs.FsBlobStore;
import com.bulmerkiln.storage.service.SingleStoreRouter;
import com.bulmerkiln.web.FormatRequests;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The npm routes wired to real services: PostgreSQL with every migration, a filesystem blob store,
 * stored role bindings, and a caller the test chooses. Namespace {@code acme} (private) owns
 * {@code @acme} and unscoped names starting {@code acme-}; {@code pub} (public) owns {@code @pub}.
 */
public final class NpmHarness implements AutoCloseable {

    public static final Principal WRITER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    static final Principal ADMIN = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal STRANGER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ANONYMOUS = new Principal.Anonymous();

    private final Database database;
    private final Organization acme;
    private final MockMvc mvc;
    private final List<String> events = new ArrayList<>();
    private Principal caller = WRITER;

    public NpmHarness(Path blobs, String prefix) throws IOException {
        database = KilnPostgres.database();
        acme = KilnPostgres.organization(database, "acme");
        StoredRoleBindingLookup bindings = new StoredRoleBindingLookup(
                database, KilnPostgres.repository(database, RoleBindingRepository.class), new RoleBindingMapperImpl());
        OrganizationContext.run(acme, () -> {
            database.run(tx -> {
                NamespaceStore.create(tx, namespace("acme", Visibility.PRIVATE, "@acme", List.of("acme-")));
                NamespaceStore.create(tx, namespace("pub", Visibility.PUBLIC, "@pub", List.of()));
            });
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("pub")));
            bindings.add(new RoleBinding(member(ADMIN), Role.ADMIN, Scope.organization()));
        });
        OrganizationResolver organizations = path -> path.startsWith(prefix)
                ? Optional.of(new OrganizationResolver.Resolution(acme, path.substring(prefix.length())))
                : Optional.empty();
        NpmAccessService access = new NpmAccessService(
                database,
                new FormatAccess(new RoleBasedPolicy(bindings), new NamespaceVisibilityLookup(database)),
                new RegistryPolicy(database));
        ArtifactEventService recorded = new ArtifactEventService(List.of(new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {
                events.add("pushed " + artifact.getName() + " " + artifact.getVersion());
            }

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {
                events.add("pulled " + artifact.getName() + " " + artifact.getVersion());
            }
        }));
        StorageRouter storage = new SingleStoreRouter(new FsBlobStore(blobs));
        mvc = MockMvcBuilders.standaloneSetup(new NpmController(new NpmRouteDispatcher(
                        new FormatRequests(organizations, request -> caller, "http://localhost"),
                        new NpmPackageHandler(
                                new NpmReadService(database, access, storage, recorded),
                                new NpmPublishService(database, access, storage, recorded, 1024),
                                new NpmTagService(database, access)),
                        new NpmUnpublishHandler(new NpmUnpublishService(database, access)))))
                .build();
    }

    public MockMvc mvc() {
        return mvc;
    }

    Database database() {
        return database;
    }

    Organization acme() {
        return acme;
    }

    List<String> events() {
        return events;
    }

    public void callAs(Principal principal) {
        caller = principal;
    }

    /** The document {@code npm publish} sends for one version, with its real integrity. */
    public static String publishBody(String name, String version, String tarball) {
        byte[] bytes = tarball.getBytes(StandardCharsets.UTF_8);
        String integrity = "sha512-" + Base64.getEncoder().encodeToString(hash("SHA-512", bytes));
        String shasum = HexFormat.of().formatHex(hash("SHA-1", bytes));
        return publishBody(name, version, tarball, integrity, shasum);
    }

    public static String publishBody(String name, String version, String tarball, String integrity, String shasum) {
        String file = name.substring(name.indexOf('/') + 1) + "-" + version + ".tgz";
        String data = Base64.getEncoder().encodeToString(tarball.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, """
                {"_id":"%1$s","name":"%1$s","description":"A package","dist-tags":{"latest":"%2$s"},
                 "versions":{"%2$s":{"name":"%1$s","version":"%2$s","description":"A package",
                   "dependencies":{"left-pad":"^1.0.0"},
                   "dist":{"integrity":"%3$s","shasum":"%4$s","tarball":"http://elsewhere/x.tgz"}}},
                 "_attachments":{"%5$s":{"content_type":"application/octet-stream","data":"%6$s","length":%7$d}}}
                """, name, version, integrity, shasum, file, data, tarball.length());
    }

    /**
     * A request for exactly this path. MockMvc treats a string as a URI template and would encode
     * npm's {@code %2f} again.
     */
    public static MockHttpServletRequestBuilder at(HttpMethod method, String path) {
        return request(method, URI.create(path));
    }

    /** Publishes a version and returns the response. */
    public MvcResult publish(String path, String body) throws Exception {
        return mvc.perform(at(HttpMethod.PUT, "/npm/" + path)
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    @Override
    public void close() {
        database.close();
    }

    private static byte[] hash(String algorithm, byte[] bytes) {
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Namespace namespace(String name, Visibility visibility, String scope, List<String> prefixes) {
        return new Namespace(UUID.randomUUID(), name, visibility, null, scope, prefixes, List.of(), List.of());
    }

    private static RoleBinding.Subject member(Principal principal) {
        return new RoleBinding.Subject.Member(((Principal.Member) principal).getId());
    }
}
