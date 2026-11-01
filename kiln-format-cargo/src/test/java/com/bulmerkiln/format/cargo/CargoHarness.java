/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.cargo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationResolver;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.format.ArtifactEventListener;
import com.bulmerkiln.format.cargo.repository.CargoVersionRepository;
import com.bulmerkiln.format.cargo.service.CargoAccessService;
import com.bulmerkiln.format.cargo.service.CargoCrateService;
import com.bulmerkiln.format.cargo.service.CargoPublishService;
import com.bulmerkiln.format.cargo.service.CargoReadService;
import com.bulmerkiln.format.cargo.web.CargoController;
import com.bulmerkiln.format.cargo.web.CargoRouteDispatcher;
import com.bulmerkiln.format.domain.ArtifactRef;
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
import com.bulmerkiln.registry.KilnRegistry;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.NamespaceClaim;
import com.bulmerkiln.storage.StorageRouter;
import com.bulmerkiln.storage.fs.FsBlobStore;
import com.bulmerkiln.storage.service.SingleStoreRouter;
import com.bulmerkiln.web.FormatRequests;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The Cargo routes wired to real services: PostgreSQL with every migration, a filesystem blob store,
 * stored role bindings, and a caller the test chooses. Namespace {@code acme} (private) owns crates
 * named {@code acme-*}; {@code pub} (public) owns {@code pub-*}.
 */
public final class CargoHarness implements AutoCloseable {

    public static final Principal WRITER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ADMIN = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal STRANGER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());
    public static final Principal ANONYMOUS = new Principal.Anonymous();

    private final Database database;
    private final Organization acme;
    private final KilnRegistry registry;
    private final MockMvc mvc;
    private final List<String> events = new ArrayList<>();
    private Principal caller = WRITER;

    public CargoHarness(Path blobs, String prefix) throws IOException {
        database = KilnPostgres.database();
        acme = KilnPostgres.organization(database, "acme");
        registry = KilnRegistry.of(database);
        StoredRoleBindingLookup bindings = new StoredRoleBindingLookup(
                database, KilnPostgres.repository(database, RoleBindingRepository.class), new RoleBindingMapperImpl());
        OrganizationContext.run(acme, () -> {
            database.run(() -> {
                namespace("acme", Visibility.PRIVATE, "acme-");
                namespace("pub", Visibility.PUBLIC, "pub-");
            });
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("acme")));
            bindings.add(new RoleBinding(member(WRITER), Role.WRITER, Scope.namespace("pub")));
            bindings.add(new RoleBinding(member(ADMIN), Role.ADMIN, Scope.organization()));
        });
        OrganizationResolver organizations = path -> path.startsWith(prefix)
                ? Optional.of(new OrganizationResolver.Resolution(acme, path.substring(prefix.length())))
                : Optional.empty();
        CargoAccessService access = new CargoAccessService(
                database,
                registry.getNamespaces(),
                registry.getBlocks(),
                new FormatAccess(new RoleBasedPolicy(bindings), registry.getVisibility()),
                registry.getPolicy());
        CargoCrateService crates = new CargoCrateService(
                registry.getPackages(),
                registry.getBlobs(),
                KilnPostgres.repository(database, CargoVersionRepository.class));
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
        mvc = MockMvcBuilders.standaloneSetup(new CargoController(new CargoRouteDispatcher(
                        new FormatRequests(organizations, request -> caller, "http://localhost"),
                        new CargoReadService(database, access, crates, storage, recorded),
                        new CargoPublishService(database, access, crates, storage, recorded, 64))))
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

    /** What {@code cargo publish} sends: the metadata and the crate, each after its length. */
    static byte[] publishBody(String metadata, String crate) {
        byte[] json = metadata.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = crate.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(8 + json.length + bytes.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(json.length)
                .put(json)
                .putInt(bytes.length)
                .put(bytes)
                .array();
    }

    static String metadata(String name, String version) {
        return String.format(Locale.ROOT, """
                {"name":"%s","vers":"%s","deps":[
                  {"name":"serde","version_req":"^1.0","features":["derive"],"optional":false,
                   "default_features":true,"target":null,"kind":"normal","registry":null},
                  {"name":"tokio","version_req":"^1","features":[],"optional":true,"default_features":false,
                   "target":"cfg(unix)","kind":"dev","registry":null,"explicit_name_in_toml":"rt"}],
                 "features":{"full":["rt"]},"authors":["a"],"description":"A crate","links":null,
                 "rust_version":"1.80"}
                """, name, version);
    }

    /** Publishes a crate and returns the status. */
    public int publish(String name, String version, String crate) throws Exception {
        return mvc.perform(at(HttpMethod.PUT, "/cargo/api/v1/crates/new")
                        .content(publishBody(metadata(name, version), crate)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Override
    public void close() {
        database.close();
    }

    /** A namespace claiming a crate name prefix: {@code acme-} owns {@code acme-core}. */
    private void namespace(String name, Visibility visibility, String prefix) {
        registry.getNamespaces()
                .create(
                        new Namespace(UUID.randomUUID(), name, visibility),
                        List.of(new NamespaceClaim(CargoFormatModule.FORMAT, prefix, null)));
    }

    /** The registry's services over this harness's database. */
    KilnRegistry registry() {
        return registry;
    }

    private static RoleBinding.Subject member(Principal principal) {
        return new RoleBinding.Subject.Member(((Principal.Member) principal).getId());
    }
}
