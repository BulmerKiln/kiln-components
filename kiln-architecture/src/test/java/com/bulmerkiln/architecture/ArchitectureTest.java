/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The architecture rules (ARCH-1 to ARCH-8), checked against every module's production classes. */
class ArchitectureTest {

    private static final String KILN = "com.bulmerkiln.";
    private static final String AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.AutoConfiguration";
    private static final String CONFIGURATION_PROPERTIES =
            "org.springframework.boot.context.properties.ConfigurationProperties";

    /**
     * Each module's package, and the modules it may depend on (ARCH-2). A dependency not listed here
     * fails the build, so this table is the module dependency graph. It's acyclic by construction.
     */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("core", Set.of()),
            Map.entry("storage", Set.of("core")),
            Map.entry("storage.fs", Set.of("storage", "core")),
            Map.entry("storage.s3", Set.of("storage", "core")),
            Map.entry("format", Set.of("core")),
            Map.entry("format.oci", Set.of("format", "core")),
            Map.entry("format.npm", Set.of("format", "core")),
            Map.entry("format.maven", Set.of("format", "core")),
            Map.entry("format.cargo", Set.of("format", "core")),
            Map.entry("auth", Set.of()),
            Map.entry("auth.oidc", Set.of("auth")),
            Map.entry("auth.oauth2", Set.of("auth")),
            Map.entry("permissions", Set.of("format", "core")),
            Map.entry("scan", Set.of("core")),
            Map.entry("scan.orchestration", Set.of("scan", "core")),
            Map.entry("scan.trivy", Set.of("scan", "core")),
            Map.entry("scan.grype", Set.of("scan", "core")),
            Map.entry("scan.osv", Set.of("scan", "core")),
            Map.entry("scan.gitleaks", Set.of("scan", "core")),
            Map.entry("observability", Set.of("format", "core")));

    /**
     * Dependencies in {@link #ALLOWED} that are optional: the module works without them, and reaches
     * them only through an auto-configuration annotated with {@code @ConditionalOnClass} (ARCH-11).
     */
    static final Map<String, Set<String>> OPTIONAL =
            Map.of("permissions", Set.of("format"), "observability", Set.of("format"));

    /** Each contract, and the suffix its implementations are named with (ARCH-10). */
    static final Map<String, String> CONTRACT_NAMES = Map.ofEntries(
            Map.entry("com.bulmerkiln.format.FormatModule", "FormatModule"),
            Map.entry("com.bulmerkiln.format.ArtifactAccess", "Access"),
            Map.entry("com.bulmerkiln.format.ArtifactEventListener", "Listener"),
            Map.entry("com.bulmerkiln.storage.BlobStore", "BlobStore"),
            Map.entry("com.bulmerkiln.storage.StorageRouter", "Router"),
            Map.entry("com.bulmerkiln.auth.connector.IdentityConnector", "Connector"),
            Map.entry("com.bulmerkiln.auth.connector.ConnectorSource", "Connectors"),
            Map.entry("com.bulmerkiln.auth.connector.ConnectorRegistry", "Registry"),
            Map.entry("com.bulmerkiln.permissions.PermissionPolicy", "Policy"),
            Map.entry("com.bulmerkiln.scan.ScannerAdapter", "Adapter"));

    private static final String CONDITIONAL_ON_CLASS =
            "org.springframework.boot.autoconfigure.condition.ConditionalOnClass";

    /** The modules the sandboxed scan worker loads (ARCH-4). */
    static final Set<String> WORKER = Set.of("scan", "scan.trivy", "scan.grype", "scan.osv", "scan.gitleaks");

    private static final JavaClasses KILN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bulmerkiln");

    /** Guards every other rule: they'd pass trivially if nothing were imported. */
    @Test
    void importsEveryModulesProductionClasses() {
        Set<String> found = new TreeSet<>();
        for (JavaClass javaClass : KILN_CLASSES) {
            moduleOf(javaClass.getPackageName()).ifPresent(found::add);
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(ALLOWED.keySet());
        assertThat(KILN_CLASSES.stream().map(JavaClass::getSimpleName))
                .doesNotContain("FsBlobStoreTest", "BlobStoreContract");
    }

    @Test
    void theKernelDependsOnNothingButTheJdk() {
        classes()
                .that()
                .resideInAPackage("com.bulmerkiln.core..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("com.bulmerkiln.core..", "java..", "org.jspecify..")
                .check(KILN_CLASSES);
    }

    @Test
    void modulesDependOnlyOnTheModulesTheyMay() {
        classes().should(onlyDependOnAllowedModules()).check(KILN_CLASSES);
    }

    @Test
    void subsystemsHaveNoCycles() {
        slices().matching("com.bulmerkiln.(*)..").should().beFreeOfCycles().check(KILN_CLASSES);
    }

    @Test
    void theScanWorkerCarriesNoServerCode() {
        noClasses()
                .that(resideInModules(WORKER))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "io.micrometer..")
                .check(KILN_CLASSES);
    }

    @Test
    void springIsOnlyInAutoConfigurationsAndProperties() {
        noClasses()
                .that()
                .haveSimpleNameNotEndingWith("AutoConfiguration")
                .and()
                .haveSimpleNameNotEndingWith("Properties")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(KILN_CLASSES);
    }

    @Test
    void autoConfigurationsAndPropertiesAreNamedForWhatTheyAre() {
        classes()
                .that()
                .areAnnotatedWith(AUTO_CONFIGURATION)
                .should()
                .haveSimpleNameEndingWith("AutoConfiguration")
                .check(KILN_CLASSES);
        classes()
                .that()
                .haveSimpleNameEndingWith("AutoConfiguration")
                .should()
                .beAnnotatedWith(AUTO_CONFIGURATION)
                .check(KILN_CLASSES);
        classes()
                .that()
                .areAnnotatedWith(CONFIGURATION_PROPERTIES)
                .should()
                .haveSimpleNameEndingWith("Properties")
                .check(KILN_CLASSES);
    }

    @Test
    void implementationsAreNamedForTheirContract() {
        CONTRACT_NAMES.forEach((contract, suffix) -> classes()
                .that()
                .areAssignableTo(contract)
                .and()
                .areNotInterfaces()
                .and()
                .areNotAnonymousClasses()
                .should()
                .haveSimpleNameEndingWith(suffix)
                .allowEmptyShould(true)
                .check(KILN_CLASSES));
    }

    @Test
    void optionalModulesAreReachedOnlyThroughConditionalAutoConfigurations() {
        classes().should(reachOptionalModulesOnlyConditionally()).check(KILN_CLASSES);
    }

    @Test
    void exceptionsAreNamedExceptions() {
        classes()
                .that()
                .areAssignableTo(Throwable.class)
                .should()
                .haveSimpleNameEndingWith("Exception")
                .check(KILN_CLASSES);
    }

    @Test
    void followsGeneralCodingRules() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(KILN_CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(KILN_CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(KILN_CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(KILN_CLASSES);
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME.check(KILN_CLASSES);
    }

    /**
     * A class in a module that uses an optional dependency must be a conditional auto-configuration,
     * or be used only by those and by other classes that use the optional dependency.
     */
    private static ArchCondition<JavaClass> reachOptionalModulesOnlyConditionally() {
        return new ArchCondition<>("reach optional modules only through @ConditionalOnClass auto-configurations") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                if (!usesOptionalModule(origin) || isConditionalAutoConfiguration(origin)) {
                    return;
                }
                for (Dependency use : origin.getDirectDependenciesToSelf()) {
                    JavaClass user = use.getOriginClass();
                    boolean sameModule = moduleOf(user.getPackageName()).equals(moduleOf(origin.getPackageName()));
                    if (sameModule && !usesOptionalModule(user) && !isConditionalAutoConfiguration(user)) {
                        events.add(SimpleConditionEvent.violated(
                                use,
                                use.getDescription() + ", but " + origin.getSimpleName() + " uses an optional module"));
                    }
                }
            }
        };
    }

    private static boolean usesOptionalModule(JavaClass javaClass) {
        Set<String> optional = moduleOf(javaClass.getPackageName())
                .map(m -> OPTIONAL.getOrDefault(m, Set.of()))
                .orElse(Set.of());
        return javaClass.getDirectDependenciesFromSelf().stream()
                .map(d -> moduleOf(d.getTargetClass().getPackageName()))
                .anyMatch(target -> target.filter(optional::contains).isPresent());
    }

    private static boolean isConditionalAutoConfiguration(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(AUTO_CONFIGURATION) && javaClass.isAnnotatedWith(CONDITIONAL_ON_CLASS);
    }

    /** The module a Kiln package belongs to: the longest matching entry in {@link #ALLOWED}. */
    static Optional<String> moduleOf(String packageName) {
        if (!packageName.startsWith(KILN)) {
            return Optional.empty();
        }
        String relative = packageName.substring(KILN.length());
        return ALLOWED.keySet().stream()
                .filter(module -> relative.equals(module) || relative.startsWith(module + "."))
                .max((a, b) -> Integer.compare(a.length(), b.length()));
    }

    private static DescribedPredicate<JavaClass> resideInModules(Set<String> modules) {
        return DescribedPredicate.describe(
                "reside in " + modules,
                javaClass -> moduleOf(javaClass.getPackageName())
                        .filter(modules::contains)
                        .isPresent());
    }

    private static ArchCondition<JavaClass> onlyDependOnAllowedModules() {
        return new ArchCondition<>("only depend on their own module and the modules ALLOWED lists") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> from = moduleOf(origin.getPackageName());
                if (from.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            origin, origin.getName() + " is in no module; add its package to ALLOWED"));
                    return;
                }
                Set<String> allowed = ALLOWED.get(from.get());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getPackageName();
                    Optional<String> to = moduleOf(target);
                    if (to.isPresent() && !to.get().equals(from.get()) && !allowed.contains(to.get())) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
