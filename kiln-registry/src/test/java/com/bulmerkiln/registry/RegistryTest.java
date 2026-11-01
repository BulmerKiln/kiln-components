/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import com.bulmerkiln.permissions.domain.Ids.MemberId;
import com.bulmerkiln.permissions.domain.Principal;
import com.bulmerkiln.permissions.domain.Scope;
import com.bulmerkiln.registry.domain.BlobChecksums;
import com.bulmerkiln.registry.domain.Namespace;
import com.bulmerkiln.registry.domain.NamespaceClaim;
import com.bulmerkiln.registry.domain.PackageFile;
import com.bulmerkiln.registry.domain.PackageVersion;
import com.bulmerkiln.registry.domain.RegistryPackage;
import com.bulmerkiln.registry.service.BlockService;
import com.bulmerkiln.registry.service.NamespaceService;
import com.bulmerkiln.registry.service.PackageService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Against PostgreSQL with every registry migration, as the runtime role. */
class RegistryTest {

    private static final ArtifactFormat OCI = new ArtifactFormat("oci");
    private static final ArtifactFormat NPM = new ArtifactFormat("npm");
    private static final ArtifactFormat MAVEN = new ArtifactFormat("maven");
    private static final ArtifactFormat CARGO = new ArtifactFormat("cargo");
    private static final ArtifactFormat GO = new ArtifactFormat("go");
    private static final Digest TARBALL = Digest.sha256("tarball".getBytes(StandardCharsets.UTF_8));
    private static final Digest OTHER = Digest.sha256("other".getBytes(StandardCharsets.UTF_8));
    private static final Principal PUBLISHER = new Principal.Member(new MemberId(UUID.randomUUID()), Set.of());

    private static Database database;
    private static KilnRegistry registry;
    private static Organization acme;
    private static Organization globex;

    @BeforeAll
    static void start() {
        database = KilnPostgres.database();
        registry = KilnRegistry.of(database);
        acme = KilnPostgres.organization(database, "acme");
        globex = KilnPostgres.organization(database, "globex");
        in(acme, () -> {
            namespaces()
                    .create(
                            new Namespace(UUID.randomUUID(), "acme", Visibility.PUBLIC),
                            List.of(
                                    new NamespaceClaim(OCI, "acme", "/"),
                                    new NamespaceClaim(NPM, "@acme", "/"),
                                    new NamespaceClaim(NPM, "acme-", null),
                                    new NamespaceClaim(MAVEN, "org.acme", "."),
                                    new NamespaceClaim(CARGO, "acme-", null),
                                    new NamespaceClaim(GO, "example.com/acme", "/")));
            namespaces()
                    .create(
                            new Namespace(UUID.randomUUID(), "team", Visibility.PRIVATE),
                            List.of(
                                    new NamespaceClaim(OCI, "acme/team", "/"),
                                    new NamespaceClaim(NPM, "acme-team-", null),
                                    new NamespaceClaim(MAVEN, "org.acme.team", "."),
                                    new NamespaceClaim(CARGO, "acme-team-", null)));
            return true;
        });
    }

    @AfterAll
    static void stop() {
        database.close();
    }

    /**
     * REG-1: the longest claim wins in every format, a claim matches only at its separator, claims
     * are per format, and a name no namespace claims has none.
     */
    @Test
    void findsTheNamespaceOwningEachName() {
        in(acme, () -> {
            assertThat(owner(OCI, "acme")).contains("acme");
            assertThat(owner(OCI, "acme/api")).contains("acme");
            assertThat(owner(OCI, "acme/team/app")).contains("team");
            assertThat(owner(OCI, "acmecorp/api")).isEmpty();
            assertThat(owner(NPM, "@acme/ui")).contains("acme");
            assertThat(owner(NPM, "@acmex/ui")).isEmpty();
            assertThat(owner(NPM, "acme-ui")).contains("acme");
            assertThat(owner(NPM, "acme-team-ui")).contains("team");
            assertThat(owner(NPM, "left-pad")).isEmpty();
            assertThat(owner(MAVEN, "org.acme")).contains("acme");
            assertThat(owner(MAVEN, "org.acme.core")).contains("acme");
            assertThat(owner(MAVEN, "org.acme.team.app")).contains("team");
            assertThat(owner(MAVEN, "org.acmecorp")).isEmpty();
            assertThat(owner(CARGO, "acme-core")).contains("acme");
            assertThat(owner(CARGO, "acme-team-app")).contains("team");
            assertThat(owner(CARGO, "serde")).isEmpty();
            assertThat(owner(CARGO, "acme/api")).isEmpty();
            assertThat(owner(GO, "example.com/acme/tool")).contains("acme");
            return true;
        });
    }

    /** REG-1: a name claimed in a format is claimed once, whichever namespace asks. */
    @Test
    void refusesASecondClaimOnAName() {
        assertThatThrownBy(() -> in(acme, () -> {
                    namespaces()
                            .create(
                                    new Namespace(UUID.randomUUID(), "squatter", Visibility.PUBLIC),
                                    List.of(new NamespaceClaim(OCI, "acme", "/")));
                    return true;
                }))
                .isInstanceOf(RuntimeException.class);
        assertThat(in(acme, () -> namespaces().byName("squatter"))).isEmpty();
    }

    @Test
    void readsANamespaceBack() {
        Namespace team = in(acme, () -> namespaces().byName("team")).orElseThrow();

        assertThat(team.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(in(acme, () -> namespaces().byId(team.getId()))).contains(team);
        assertThat(in(acme, () -> namespaces().byId(UUID.randomUUID()))).isEmpty();
    }

    /** REG-1: namespaces are per organization; another can own the same names. */
    @Test
    void keepsNamespacesPerOrganization() {
        assertThat(in(globex, () -> owner(NPM, "@acme/ui"))).isEmpty();
        in(globex, () -> {
            namespaces()
                    .create(
                            new Namespace(UUID.randomUUID(), "acme", Visibility.PRIVATE),
                            List.of(new NamespaceClaim(NPM, "@acme", "/")));
            return true;
        });
        assertThat(in(globex, () -> namespaces().owner(NPM, "@acme/ui").map(Namespace::getVisibility)))
                .contains(Visibility.PRIVATE);
        assertThat(in(acme, () -> namespaces().owner(NPM, "@acme/ui").map(Namespace::getVisibility)))
                .contains(Visibility.PUBLIC);
    }

    /** REG-5: visibility comes from the namespace; anything else is private. */
    @Test
    void takesVisibilityFromTheNamespace() {
        assertThat(OrganizationContext.call(acme, () -> registry.getVisibility().of(Scope.repository("acme", "api"))))
                .isEqualTo(Visibility.PUBLIC);
        assertThat(OrganizationContext.call(acme, () -> registry.getVisibility().of(Scope.namespace("team"))))
                .isEqualTo(Visibility.PRIVATE);
        assertThat(OrganizationContext.call(acme, () -> registry.getVisibility().of(Scope.namespace("missing"))))
                .isEqualTo(Visibility.PRIVATE);
        assertThat(OrganizationContext.call(acme, () -> registry.getVisibility().of(Scope.organization())))
                .isEqualTo(Visibility.PRIVATE);
    }

    /** REG-2: recording a blob twice, as concurrent pushes do, is harmless, and keeps its checksums. */
    @Test
    void recordsBlobsIdempotently() {
        Digest digest = Digest.sha256("checksummed".getBytes(StandardCharsets.UTF_8));
        Digest sha512 = Digest.parse("sha512:" + "ab".repeat(64));
        in(acme, () -> {
            registry.getBlobs().record(sha512, 3);
            registry.getBlobs().record(sha512, 3, new BlobChecksums(null, null, "given", null));
            registry.getBlobs().record(TARBALL, 7);
            registry.getBlobs().record(TARBALL, 7);
            registry.getBlobs().record(digest, 11, new BlobChecksums("md5", "sha1", null, null));
            registry.getBlobs().record(digest, 11, new BlobChecksums(null, "other", null, "sha512"));
            return true;
        });

        assertThat(in(acme, () -> registry.getBlobs().size(TARBALL))).contains(7L);
        assertThat(in(acme, () -> registry.getBlobs().size(Digest.sha256(new byte[0]))))
                .isEmpty();
        assertThat(in(globex, () -> registry.getBlobs().size(TARBALL))).isEmpty();
        assertThat(in(acme, () -> registry.getBlobs().checksums(digest)))
                .contains(new BlobChecksums("md5", "sha1", digest.getEncoded(), "sha512"));
        assertThat(in(acme, () -> registry.getBlobs().checksums(TARBALL)))
                .contains(new BlobChecksums(null, null, TARBALL.getEncoded(), null));
        assertThat(in(acme, () -> registry.getBlobs().checksums(sha512)))
                .contains(new BlobChecksums(null, null, "given", null));
        assertThat(in(acme, () -> registry.getBlobs().checksums(Digest.sha256(new byte[0]))))
                .isEmpty();
    }

    /** REG-3: versions and files never change once written; the same bytes again is a no-op. */
    @Test
    void keepsVersionsAndFilesImmutable() {
        UUID namespace =
                in(acme, () -> namespaces().byName("acme")).orElseThrow().getId();
        RegistryPackage pkg = in(acme, () -> packages().create(NPM, namespace, "@acme/util", "@acme/util"));
        assertThat(in(acme, () -> packages().create(NPM, namespace, "@acme/util", "@acme/util")))
                .isEqualTo(pkg);

        PackageVersion version = in(acme, () -> {
            registry.getBlobs().record(TARBALL, 7);
            registry.getBlobs().record(OTHER, 5);
            PackageVersion added = packages().addVersion(pkg.getId(), "1.0.0", PUBLISHER);
            packages().addFile(added.getId(), "util-1.0.0.tgz", TARBALL, 7);
            packages().addFile(added.getId(), "util-1.0.0.tgz", TARBALL, 7);
            return added;
        });

        assertThat(refusal(() -> in(acme, () -> packages().addVersion(pkg.getId(), "1.0.0", PUBLISHER))))
                .extracting(Refusal::getReason)
                .isEqualTo(Reason.CONFLICT);
        assertThat(refusal(() -> in(acme, () -> packages().addFile(version.getId(), "util-1.0.0.tgz", OTHER, 5))))
                .extracting(Refusal::getReason)
                .isEqualTo(Reason.CONFLICT);
        assertThat(in(acme, () -> packages().files(version.getId())))
                .extracting(PackageFile::getName, PackageFile::getDigest, PackageFile::getSize)
                .containsExactly(tuple("util-1.0.0.tgz", TARBALL, 7L));
    }

    /** REG-8: an unpublished version goes with its files, and then the package can go too. */
    @Test
    void deletesVersionsAndPackages() {
        UUID namespace =
                in(acme, () -> namespaces().byName("acme")).orElseThrow().getId();
        RegistryPackage pkg = in(acme, () -> packages().create(NPM, namespace, "@acme/gone", "@acme/gone"));
        PackageVersion version = in(acme, () -> {
            registry.getBlobs().record(TARBALL, 7);
            PackageVersion added = packages().addVersion(pkg.getId(), "1.0.0", PUBLISHER);
            packages().addFile(added.getId(), "gone-1.0.0.tgz", TARBALL, 7);
            return added;
        });

        in(acme, () -> {
            packages().deleteVersion(version.getId());
            return true;
        });
        assertThat(in(acme, () -> packages().versions(pkg.getId()))).isEmpty();
        assertThat(in(acme, () -> packages().files(version.getId()))).isEmpty();
        assertThat(in(acme, () -> registry.getBlobs().size(TARBALL))).contains(7L);

        in(acme, () -> {
            packages().delete(pkg.getId());
            return true;
        });
        assertThat(in(acme, () -> packages().find(NPM, "@acme/gone"))).isEmpty();
    }

    /** REG-3: a version published file by file is created once and then grows. */
    @Test
    void ensuresVersionsPublishedFileByFile() {
        UUID namespace =
                in(acme, () -> namespaces().byName("acme")).orElseThrow().getId();
        PackageVersion first = in(acme, () -> {
            RegistryPackage pkg = packages().create(MAVEN, namespace, "org.acme:core", "org.acme:core");
            return packages().ensureVersion(pkg.getId(), "1.0", PUBLISHER);
        });
        PackageVersion again = in(acme, () -> {
            RegistryPackage pkg = packages().find(MAVEN, "org.acme:core").orElseThrow();
            return packages().ensureVersion(pkg.getId(), "1.0", PUBLISHER);
        });

        assertThat(again.getId()).isEqualTo(first.getId());
    }

    /** REG-9: every change a format shows moves the package's last change, and reading doesn't. */
    @Test
    void marksThePackageChanged() {
        UUID namespace =
                in(acme, () -> namespaces().byName("acme")).orElseThrow().getId();
        RegistryPackage created = in(acme, () -> packages().create(NPM, namespace, "@acme/tags", "@acme/tags"));
        Instant afterCreate = updated("@acme/tags");
        PackageVersion second = in(acme, () -> {
            packages().addVersion(created.getId(), "1.0.0", PUBLISHER);
            return packages().addVersion(created.getId(), "2.0.0", PUBLISHER);
        });
        Instant afterVersions = updated("@acme/tags");
        in(acme, () -> packages().version(created.getId(), "2.0.0"));
        assertThat(updated("@acme/tags")).isEqualTo(afterVersions);
        in(acme, () -> {
            packages().touch(created.getId());
            return true;
        });
        Instant afterTouch = updated("@acme/tags");
        in(acme, () -> {
            packages().deleteVersion(second.getId());
            return true;
        });

        assertThat(afterVersions).isAfter(afterCreate);
        assertThat(afterTouch).isAfter(afterVersions);
        assertThat(updated("@acme/tags")).isAfter(afterTouch);
        assertThat(in(acme, () -> packages().versions(created.getId())))
                .extracting(PackageVersion::getVersion)
                .containsExactly("1.0.0");
        assertThat(in(acme, () -> packages().file(second.getId(), "missing.tgz")))
                .isEmpty();
        assertThat(in(globex, () -> packages().find(NPM, "@acme/tags"))).isEmpty();
    }

    /** REG-4: a block refuses reads and writes with 403, a tombstone refuses republishing. */
    @Test
    void refusesBlockedAndTombstonedArtifacts() {
        ArtifactRef bad = new ArtifactRef(NPM, "acme", "@acme/bad", "1.0.0");
        ArtifactRef good = new ArtifactRef(NPM, "acme", "@acme/bad", "1.0.1");
        ArtifactRef gone = new ArtifactRef(NPM, "acme", "@acme/gone", "2.0.0");
        ArtifactRef evil = new ArtifactRef(NPM, "acme", "@acme/evil", "9.9.9");
        in(acme, () -> {
            blocks().block(NPM, "@acme/bad", "1.0.0", "malware", PUBLISHER);
            blocks().block(NPM, "@acme/bad", "1.0.0", "again", PUBLISHER);
            blocks().block(NPM, "@acme/evil", null, "typosquat", PUBLISHER);
            blocks().tombstone(NPM, "@acme/gone", "2.0.0");
            blocks().tombstone(NPM, "@acme/gone", "2.0.0");
            return true;
        });

        Refusal blocked = OrganizationContext.call(
                        acme, () -> registry.getPolicy().checkRead(bad))
                .orElseThrow();
        assertThat(blocked.getReason()).isEqualTo(Reason.BLOCKED);
        assertThat(blocked.getMessage()).isEqualTo("@acme/bad 1.0.0 is blocked: malware");
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkRead(evil))
                        .map(Refusal::getMessage))
                .contains("@acme/evil 9.9.9 is blocked: typosquat");
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkWrite(bad, 1))
                        .map(Refusal::getReason))
                .contains(Reason.BLOCKED);
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkWrite(gone, 1))
                        .map(Refusal::getReason))
                .contains(Reason.CONFLICT);
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkRead(gone)))
                .isEmpty();
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkRead(good)))
                .isEmpty();
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkWrite(good, 1)))
                .isEmpty();
        assertThat(OrganizationContext.call(globex, () -> registry.getPolicy().checkRead(bad)))
                .isEmpty();
        ArtifactRef unversioned = new ArtifactRef(NPM, "acme", "@acme/evil", null);
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkWrite(unversioned, 1))
                        .map(Refusal::getMessage))
                .contains("@acme/evil is blocked: typosquat");
        ArtifactRef fine = new ArtifactRef(NPM, "acme", "@acme/fine", null);
        assertThat(OrganizationContext.call(acme, () -> registry.getPolicy().checkWrite(fine, 1)))
                .isEmpty();
    }

    /** REG-4: a manual block names who set it, so an anonymous caller can't set one. */
    @Test
    void aBlockNamesWhoSetIt() {
        assertThatThrownBy(() -> in(acme, () -> {
                    blocks().block(NPM, "@acme/x", null, "no one", new Principal.Anonymous());
                    return true;
                }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** REG-6: every registry table passes the schema linter. */
    @Test
    void theRegistrySchemaIsOrganizationSafe() {
        assertThat(database.instanceTransaction(SchemaLinter::problems)).isEmpty();
    }

    private static NamespaceService namespaces() {
        return registry.getNamespaces();
    }

    private static PackageService packages() {
        return registry.getPackages();
    }

    private static BlockService blocks() {
        return registry.getBlocks();
    }

    private static Optional<String> owner(ArtifactFormat format, String name) {
        return namespaces().owner(format, name).map(Namespace::getName);
    }

    private static Instant updated(String name) {
        return in(acme, () -> packages().find(NPM, name)).orElseThrow().getUpdatedAt();
    }

    private static <T> T in(Organization organization, Supplier<T> work) {
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
