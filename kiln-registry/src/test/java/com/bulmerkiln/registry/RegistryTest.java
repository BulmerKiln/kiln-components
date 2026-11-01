/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bulmerkiln.core.artifact.ArtifactFormat;
import com.bulmerkiln.core.digest.Digest;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.database.Database;
import com.bulmerkiln.database.KilnPostgres;
import com.bulmerkiln.database.service.SchemaLinter;
import com.bulmerkiln.format.RefusedException;
import com.bulmerkiln.format.constant.Reason;
import com.bulmerkiln.format.domain.ArtifactRef;
import com.bulmerkiln.format.domain.Refusal;
import com.bulmerkiln.permissions.constant.Visibility;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.entity.PackageEntity;
import com.bulmerkiln.registry.entity.PackageFileEntity;
import com.bulmerkiln.registry.entity.PackageVersionEntity;
import com.bulmerkiln.registry.service.NamespaceVisibilityLookup;
import com.bulmerkiln.registry.service.RegistryPolicy;
import com.bulmerkiln.registry.store.BlobRecordStore;
import com.bulmerkiln.registry.store.BlockStore;
import com.bulmerkiln.registry.store.NamespaceStore;
import com.bulmerkiln.registry.store.PackageStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Against PostgreSQL with every registry migration, as the runtime role. */
class RegistryTest {

    private static final ArtifactFormat NPM = new ArtifactFormat("npm");
    private static final Digest TARBALL = Digest.sha256("tarball".getBytes(StandardCharsets.UTF_8));
    private static final Digest OTHER = Digest.sha256("other".getBytes(StandardCharsets.UTF_8));

    private static Database database;
    private static Organization acme;
    private static Organization globex;

    @BeforeAll
    static void start() {
        database = KilnPostgres.database();
        acme = KilnPostgres.organization(database, "acme");
        globex = KilnPostgres.organization(database, "globex");
        in(acme, tx -> {
            NamespaceStore.create(
                    tx,
                    namespace(
                            "acme",
                            Visibility.PUBLIC,
                            "acme",
                            "@acme",
                            List.of("acme-"),
                            List.of("org.acme"),
                            List.of("acme-")));
            NamespaceStore.create(
                    tx,
                    namespace(
                            "team",
                            Visibility.PRIVATE,
                            "acme/team",
                            null,
                            List.of("acme-team-"),
                            List.of("org.acme.team"),
                            List.of("acme-team-")));
            return null;
        });
    }

    @AfterAll
    static void stop() {
        database.close();
    }

    /** REG-1: the longest prefix wins in every format, and a name no namespace owns has none. */
    @Test
    void findsTheNamespaceOwningEachName() {
        in(acme, tx -> {
            assertThat(name(NamespaceStore.forOci(tx, "acme"))).contains("acme");
            assertThat(name(NamespaceStore.forOci(tx, "acme/api"))).contains("acme");
            assertThat(name(NamespaceStore.forOci(tx, "acme/team/app"))).contains("team");
            assertThat(NamespaceStore.forOci(tx, "acmecorp/api")).isEmpty();
            assertThat(name(NamespaceStore.forNpm(tx, "@acme"))).contains("acme");
            assertThat(NamespaceStore.forNpm(tx, "@other")).isEmpty();
            assertThat(name(NamespaceStore.forNpmName(tx, "acme-ui"))).contains("acme");
            assertThat(name(NamespaceStore.forNpmName(tx, "acme-team-ui"))).contains("team");
            assertThat(NamespaceStore.forNpmName(tx, "left-pad")).isEmpty();
            assertThat(name(NamespaceStore.forMaven(tx, "org.acme"))).contains("acme");
            assertThat(name(NamespaceStore.forMaven(tx, "org.acme.core"))).contains("acme");
            assertThat(name(NamespaceStore.forMaven(tx, "org.acme.team.app"))).contains("team");
            assertThat(NamespaceStore.forMaven(tx, "org.acmecorp")).isEmpty();
            assertThat(name(NamespaceStore.forCargo(tx, "acme-core"))).contains("acme");
            assertThat(name(NamespaceStore.forCargo(tx, "acme-team-app"))).contains("team");
            assertThat(NamespaceStore.forCargo(tx, "serde")).isEmpty();
            return null;
        });
    }

    @Test
    void readsANamespaceBackWhole() {
        Namespace team = in(acme, tx -> NamespaceStore.byName(tx, "team")).orElseThrow();

        assertThat(team.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(team.getOciPrefix()).isEqualTo("acme/team");
        assertThat(team.getNpmScope()).isNull();
        assertThat(team.getNpmPrefixes()).containsExactly("acme-team-");
        assertThat(team.getMavenGroupPrefixes()).containsExactly("org.acme.team");
        assertThat(in(acme, (DSLContext tx) -> NamespaceStore.byId(tx, team.getId())))
                .contains(team);
    }

    /** REG-1: namespaces are per organization; another can own the same names. */
    @Test
    void keepsNamespacesPerOrganization() {
        assertThat(in(globex, (DSLContext tx) -> NamespaceStore.forNpm(tx, "@acme")))
                .isEmpty();
        in(globex, tx -> {
            NamespaceStore.create(
                    tx, namespace("acme", Visibility.PRIVATE, "acme", "@acme", List.of(), List.of(), List.of()));
            return null;
        });
        assertThat(in(globex, (DSLContext tx) -> NamespaceStore.forNpm(tx, "@acme"))
                        .map(Namespace::getVisibility))
                .contains(Visibility.PRIVATE);
        assertThat(in(acme, (DSLContext tx) -> NamespaceStore.forNpm(tx, "@acme"))
                        .map(Namespace::getVisibility))
                .contains(Visibility.PUBLIC);
    }

    /** REG-5: visibility comes from the namespace; anything else is private. */
    @Test
    void takesVisibilityFromTheNamespace() {
        NamespaceVisibilityLookup visibility = new NamespaceVisibilityLookup(database);

        assertThat(OrganizationContext.call(acme, () -> visibility.of(Scope.repository("acme", "api"))))
                .isEqualTo(Visibility.PUBLIC);
        assertThat(OrganizationContext.call(acme, () -> visibility.of(Scope.namespace("team"))))
                .isEqualTo(Visibility.PRIVATE);
        assertThat(OrganizationContext.call(acme, () -> visibility.of(Scope.namespace("missing"))))
                .isEqualTo(Visibility.PRIVATE);
        assertThat(OrganizationContext.call(acme, () -> visibility.of(Scope.organization())))
                .isEqualTo(Visibility.PRIVATE);
    }

    /** REG-2: recording a blob twice, as concurrent pushes do, is harmless. */
    @Test
    void recordsBlobsIdempotently() {
        in(acme, tx -> {
            BlobRecordStore.record(tx, TARBALL, 7);
            BlobRecordStore.record(tx, TARBALL, 7);
            return null;
        });

        assertThat(in(acme, (DSLContext tx) -> BlobRecordStore.size(tx, TARBALL)))
                .contains(7L);
        assertThat(in(acme, (DSLContext tx) -> BlobRecordStore.size(tx, Digest.sha256(new byte[0]))))
                .isEmpty();
        assertThat(in(globex, (DSLContext tx) -> BlobRecordStore.size(tx, TARBALL)))
                .isEmpty();
    }

    /** REG-3: versions and files never change once written; the same bytes again is a no-op. */
    @Test
    void keepsVersionsAndFilesImmutable() {
        UUID namespace =
                in(acme, tx -> NamespaceStore.byName(tx, "acme")).orElseThrow().getId();
        PackageEntity pkg = in(acme, tx -> PackageStore.create(tx, NPM, namespace, "@acme/util", "@acme/util"));
        assertThat(in(acme, (DSLContext tx) -> PackageStore.create(tx, NPM, namespace, "@acme/util", "@acme/util")))
                .isEqualTo(pkg);

        PackageVersionEntity version = in(acme, tx -> {
            BlobRecordStore.record(tx, TARBALL, 7);
            BlobRecordStore.record(tx, OTHER, 5);
            PackageVersionEntity added = PackageStore.addVersion(tx, pkg.getId(), "1.0.0", "{\"v\":1}");
            PackageStore.addFile(tx, added.getId(), "util-1.0.0.tgz", TARBALL, 7);
            PackageStore.addFile(tx, added.getId(), "util-1.0.0.tgz", TARBALL, 7);
            return added;
        });

        assertThat(refusal(() -> in(acme, tx -> PackageStore.addVersion(tx, pkg.getId(), "1.0.0", "{}"))))
                .extracting(Refusal::getReason)
                .isEqualTo(Reason.CONFLICT);
        assertThat(refusal(() -> in(acme, tx -> {
                    PackageStore.addFile(tx, version.getId(), "util-1.0.0.tgz", OTHER, 5);
                    return null;
                })))
                .extracting(Refusal::getReason)
                .isEqualTo(Reason.CONFLICT);
        assertThat(in(acme, (DSLContext tx) -> PackageStore.files(tx, version.getId())))
                .containsExactly(new PackageFileEntity("util-1.0.0.tgz", TARBALL, 7));
    }

    /** REG-8: an unpublished version goes with its files, and then the package can go too. */
    @Test
    void deletesVersionsAndPackages() {
        UUID namespace =
                in(acme, tx -> NamespaceStore.byName(tx, "acme")).orElseThrow().getId();
        PackageEntity pkg = in(acme, tx -> PackageStore.create(tx, NPM, namespace, "@acme/gone", "@acme/gone"));
        PackageVersionEntity version = in(acme, tx -> {
            BlobRecordStore.record(tx, TARBALL, 7);
            PackageVersionEntity added = PackageStore.addVersion(tx, pkg.getId(), "1.0.0", "{}");
            PackageStore.addFile(tx, added.getId(), "gone-1.0.0.tgz", TARBALL, 7);
            return added;
        });

        in(acme, tx -> {
            PackageStore.deleteVersion(tx, version.getId());
            return null;
        });
        assertThat(in(acme, (DSLContext tx) -> PackageStore.versions(tx, pkg.getId())))
                .isEmpty();
        assertThat(in(acme, (DSLContext tx) -> PackageStore.files(tx, version.getId())))
                .isEmpty();

        in(acme, tx -> {
            PackageStore.delete(tx, pkg.getId());
            return null;
        });
        assertThat(in(acme, (DSLContext tx) -> PackageStore.find(tx, NPM, "@acme/gone")))
                .isEmpty();
    }

    /** REG-3: a version published file by file is created once and then grows. */
    @Test
    void ensuresVersionsPublishedFileByFile() {
        UUID namespace = in(acme, (DSLContext tx) -> NamespaceStore.byName(tx, "acme"))
                .orElseThrow()
                .getId();
        ArtifactFormat maven = new ArtifactFormat("maven");
        PackageVersionEntity first = in(acme, (DSLContext tx) -> {
            PackageEntity pkg = PackageStore.create(tx, maven, namespace, "org.acme:core", "org.acme:core");
            return PackageStore.ensureVersion(tx, pkg.getId(), "1.0");
        });
        PackageVersionEntity again = in(acme, (DSLContext tx) -> {
            PackageEntity pkg = PackageStore.find(tx, maven, "org.acme:core").orElseThrow();
            return PackageStore.ensureVersion(tx, pkg.getId(), "1.0");
        });

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void updatesWhatFormatsMayChange() {
        UUID namespace =
                in(acme, tx -> NamespaceStore.byName(tx, "acme")).orElseThrow().getId();
        PackageVersionEntity version = in(acme, tx -> {
            PackageEntity pkg = PackageStore.create(tx, NPM, namespace, "@acme/tags", "@acme/tags");
            PackageStore.updateMetadata(tx, pkg.getId(), "{\"dist-tags\":{}}");
            PackageStore.addVersion(tx, pkg.getId(), "1.0.0", "{}");
            PackageVersionEntity second = PackageStore.addVersion(tx, pkg.getId(), "2.0.0", "{}");
            PackageStore.updateVersionMetadata(tx, second.getId(), "{\"deprecated\":\"no\"}");
            PackageStore.setYanked(tx, second.getId(), true);
            return second;
        });

        PackageEntity pkg =
                in(acme, tx -> PackageStore.find(tx, NPM, "@acme/tags")).orElseThrow();
        assertThat(pkg.getMetadata()).isEqualTo("{\"dist-tags\":{}}");
        assertThat(in(acme, (DSLContext tx) -> PackageStore.versions(tx, pkg.getId())))
                .extracting(PackageVersionEntity::getVersion)
                .containsExactly("1.0.0", "2.0.0");
        PackageVersionEntity read =
                in(acme, tx -> PackageStore.version(tx, pkg.getId(), "2.0.0")).orElseThrow();
        assertThat(read.getId()).isEqualTo(version.getId());
        assertThat(read.isYanked()).isTrue();
        assertThat(read.getMetadata()).isEqualTo("{\"deprecated\":\"no\"}");
        assertThat(in(acme, (DSLContext tx) -> PackageStore.file(tx, read.getId(), "missing.tgz")))
                .isEmpty();
        assertThat(in(globex, (DSLContext tx) -> PackageStore.find(tx, NPM, "@acme/tags")))
                .isEmpty();
    }

    /** REG-4: a block refuses reads and writes with 403, a tombstone refuses republishing. */
    @Test
    void refusesBlockedAndTombstonedArtifacts() {
        RegistryPolicy policy = new RegistryPolicy(database);
        ArtifactRef bad = new ArtifactRef(NPM, "acme", "@acme/bad", "1.0.0");
        ArtifactRef good = new ArtifactRef(NPM, "acme", "@acme/bad", "1.0.1");
        ArtifactRef gone = new ArtifactRef(NPM, "acme", "@acme/gone", "2.0.0");
        ArtifactRef evil = new ArtifactRef(NPM, "acme", "@acme/evil", "9.9.9");
        in(acme, tx -> {
            BlockStore.block(tx, NPM, "@acme/bad", "1.0.0", "malware");
            BlockStore.block(tx, NPM, "@acme/evil", null, "typosquat");
            BlockStore.tombstone(tx, NPM, "@acme/gone", "2.0.0");
            return null;
        });

        Refusal blocked =
                OrganizationContext.call(acme, () -> policy.checkRead(bad)).orElseThrow();
        assertThat(blocked.getReason()).isEqualTo(Reason.BLOCKED);
        assertThat(blocked.getMessage()).isEqualTo("@acme/bad 1.0.0 is blocked: malware");
        assertThat(OrganizationContext.call(acme, () -> policy.checkRead(evil)).map(Refusal::getMessage))
                .contains("@acme/evil 9.9.9 is blocked: typosquat");
        assertThat(OrganizationContext.call(acme, () -> policy.checkWrite(bad, 1))
                        .map(Refusal::getReason))
                .contains(Reason.BLOCKED);
        assertThat(OrganizationContext.call(acme, () -> policy.checkWrite(gone, 1))
                        .map(Refusal::getReason))
                .contains(Reason.CONFLICT);
        assertThat(OrganizationContext.call(acme, () -> policy.checkRead(gone))).isEmpty();
        assertThat(OrganizationContext.call(acme, () -> policy.checkRead(good))).isEmpty();
        assertThat(OrganizationContext.call(acme, () -> policy.checkWrite(good, 1)))
                .isEmpty();
        assertThat(OrganizationContext.call(globex, () -> policy.checkRead(bad)))
                .isEmpty();
        ArtifactRef unversioned = new ArtifactRef(NPM, "acme", "@acme/evil", null);
        assertThat(OrganizationContext.call(acme, () -> policy.checkWrite(unversioned, 1))
                        .map(Refusal::getMessage))
                .contains("@acme/evil is blocked: typosquat");
        ArtifactRef fine = new ArtifactRef(NPM, "acme", "@acme/fine", null);
        assertThat(OrganizationContext.call(acme, () -> policy.checkWrite(fine, 1)))
                .isEmpty();
    }

    @Test
    void theRegistrySchemaIsOrganizationSafe() {
        assertThat(database.instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    private static Namespace namespace(
            String name,
            Visibility visibility,
            String oci,
            String npm,
            List<String> npmPrefixes,
            List<String> maven,
            List<String> cargo) {
        return new Namespace(UUID.randomUUID(), name, visibility, oci, npm, npmPrefixes, maven, cargo);
    }

    private static Optional<String> name(Optional<Namespace> namespace) {
        return namespace.map(Namespace::getName);
    }

    private static <T> T in(Organization organization, Function<DSLContext, T> work) {
        return OrganizationContext.call(organization, () -> database.transaction(work));
    }

    private static Refusal refusal(Runnable work) {
        try {
            work.run();
        } catch (RefusedException e) {
            return e.refusal();
        }
        throw new AssertionError("Expected a refusal");
    }

    @Test
    void aRefusalHelperNeedsARefusal() {
        assertThatThrownBy(() -> refusal(() -> {})).isInstanceOf(AssertionError.class);
    }
}
