/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.maven.service.MavenAccessService;
import com.bulmerkiln.format.maven.service.MavenFileService;
import com.bulmerkiln.format.maven.service.MavenMetadataService;
import com.bulmerkiln.format.maven.store.MavenFileStore;
import com.bulmerkiln.format.maven.web.MavenController;
import com.bulmerkiln.format.maven.web.MavenRouteDispatcher;
import com.bulmerkiln.format.service.ArtifactEventService;
import com.bulmerkiln.permissions.constant.Role;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.RoleBinding;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.permissions.service.FormatAccess;
import com.bulmerkiln.permissions.service.RoleBasedPolicy;
import com.bulmerkiln.permissions.store.RoleBindingStore;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The Maven routes wired to real services: PostgreSQL with every migration, a filesystem blob store,
 * stored role bindings, and a caller the test chooses. Namespace {@code acme} (private) owns group
 * {@code org.acme}; {@code pub} (public) owns {@code org.pub}.
 */
public final class MavenHarness implements AutoCloseable {

    public static final Principal WRITER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ADMIN = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal STRANGER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ANONYMOUS = new Principal.Anonymous();

    private final Database database;
    private final Organization acme;
    private final MockMvc mvc;
    private final List<String> events = new ArrayList<>();
    private Principal caller = WRITER;

    public MavenHarness(Path blobs, String prefix) throws IOException {
        database = KilnPostgres.database();
        acme = KilnPostgres.organization(database, "acme");
        RoleBindingStore bindings = new RoleBindingStore(database);
        OrganizationContext.run(acme, () -> {
            database.run(tx -> {
                NamespaceStore.create(tx, namespace("acme", Visibility.PRIVATE, "org.acme"));
                NamespaceStore.create(tx, namespace("pub", Visibility.PUBLIC, "org.pub"));
            });
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("pub")));
            bindings.add(new RoleBinding(member(ADMIN), Role.ADMIN, Scope.organization()));
        });
        OrganizationResolver organizations = path -> path.startsWith(prefix)
                ? Optional.of(new OrganizationResolver.Resolution(acme, path.substring(prefix.length())))
                : Optional.empty();
        MavenAccessService access = new MavenAccessService(
                database,
                new FormatAccess(new RoleBasedPolicy(bindings), new NamespaceVisibilityLookup(database)),
                new RegistryPolicy(database));
        ArtifactEventService recorded = new ArtifactEventService(List.of(new ArtifactEventListener() {
            @Override
            public void pushed(ArtifactRef artifact, long bytes) {
                events.add("pushed " + artifact.name() + " " + artifact.version());
            }

            @Override
            public void pulled(ArtifactRef artifact, long bytes) {
                events.add("pulled " + artifact.name() + " " + artifact.version());
            }
        }));
        StorageRouter storage = new SingleStoreRouter(new FsBlobStore(blobs));
        mvc = MockMvcBuilders.standaloneSetup(new MavenController(new MavenRouteDispatcher(
                        new FormatRequests(organizations, request -> caller, "http://localhost"),
                        new MavenFileService(database, access, new MavenFileStore(storage, 64), recorded),
                        new MavenMetadataService(database, access),
                        access)))
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

    /** A request for exactly this path. */
    public static MockHttpServletRequestBuilder at(HttpMethod method, String path) {
        return request(method, URI.create(path));
    }

    /** Deploys one file and returns the status. */
    public int deploy(String path, String content) throws Exception {
        return mvc.perform(at(HttpMethod.PUT, "/maven/" + path).content(content.getBytes(StandardCharsets.UTF_8)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    static String sha1(String content) {
        return HexFormat.of().formatHex(hash("SHA-1", content.getBytes(StandardCharsets.UTF_8)));
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

    private static Namespace namespace(String name, Visibility visibility, String scope) {
        return new Namespace(UUID.randomUUID(), name, visibility, null, null, List.of(scope), List.of());
    }

    private static RoleBinding.Subject member(Principal principal) {
        return new RoleBinding.Subject.Member(((Principal.Member) principal).id());
    }
}
