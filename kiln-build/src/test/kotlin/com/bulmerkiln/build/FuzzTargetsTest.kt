package com.bulmerkiln.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** BUILD-14: which parsers [FuzzCheck] finds, and which calls count as fuzzing them. */
class FuzzTargetsTest {

    @Test
    fun `captures the whole name of an annotated method`() {
        val source = """
            @UntrustedInput
            public static String subject(@Nullable String subject) { return subject; }
            @UntrustedInput
            public OciManifest parse(byte[] raw, @Nullable String contentType) { return null; }
        """

        assertEquals(
            setOf(FuzzTargets.Target("T", "parse", false), FuzzTargets.Target("T", "subject", true)),
            FuzzTargets.targets("T", source),
        )
    }

    @Test
    fun `skips annotations between the marker and the name`() {
        val source = """
            @UntrustedInput
            @SuppressWarnings("unchecked")
            public static @Nullable Long contentRangeStart(@Nullable String header) { return null; }
        """

        assertEquals(setOf(FuzzTargets.Target("T", "contentRangeStart", true)), FuzzTargets.targets("T", source))
    }

    @Test
    fun `still finds unannotated static parse and fromPath methods`() {
        val source = """
            public static Digest parse(String value) { return null; }
            static MavenPath fromPath(final String path) { return null; }
            public static Digest parse(Digest other) { return other; }
        """

        assertEquals(
            setOf(FuzzTargets.Target("T", "fromPath", true), FuzzTargets.Target("T", "parse", true)),
            FuzzTargets.targets("T", source),
        )
    }

    @Test
    fun `a static parser must be called as Type method inside a fuzz body`() {
        val main = listOf("Digest" to "public static Digest parse(String value) { return null; }")
        val fuzzed = """
            class DigestTest {
                @FuzzTest
                void parses(FuzzedDataProvider data) { Digest.parse(data.consumeString(64)); }
            }
        """
        val outsideFuzzBody = """
            class DigestTest {
                @Test
                void parses() { Digest.parse("sha256:0"); }
                @FuzzTest
                void other(FuzzedDataProvider data) { Digest.of(data.consumeBytes(64)); }
            }
        """
        val unqualified = """
            class DigestTest {
                @FuzzTest
                void parses(FuzzedDataProvider data) { other.parse(data.consumeString(64)); }
            }
        """

        assertEquals(emptyList<String>(), FuzzTargets.check(main, listOf(fuzzed)).missing)
        assertEquals(listOf("Digest.parse"), FuzzTargets.check(main, listOf(outsideFuzzBody)).missing)
        assertEquals(listOf("Digest.parse"), FuzzTargets.check(main, listOf(unqualified)).missing)
    }

    @Test
    fun `an instance parser must be called inside a fuzz body in a test naming its type`() {
        val main = listOf("NpmDistVerifier" to "@UntrustedInput public void verify(String integrity) {}")
        val fuzzed = """
            class NpmDistVerifierTest {
                @FuzzTest
                void verifies(FuzzedDataProvider data) {
                    NpmDistVerifier verifier = new NpmDistVerifier();
                    verifier.verify(data.consumeString(256));
                }
            }
        """
        val otherType = """
            class OtherTest {
                @FuzzTest
                void verifies(FuzzedDataProvider data) { other.verify(data.consumeString(256)); }
            }
        """

        assertEquals(emptyList<String>(), FuzzTargets.check(main, listOf(fuzzed)).missing)
        assertEquals(listOf("NpmDistVerifier.verify"), FuzzTargets.check(main, listOf(otherType)).missing)
    }

    @Test
    fun `braces in strings, characters and comments don't end a fuzz body`() {
        val test = """
            class T {
                @FuzzTest
                void body(FuzzedDataProvider data) {
                    String json = "}{\"}";
                    char brace = '}';
                    // }
                    /* } */
                    String block = ${"\"\"\""}
                        }
                        ${"\"\"\""};
                    Digest.parse(json);
                }
                @Test
                void after() { Other.parse("x"); }
            }
        """

        val bodies = FuzzTargets.fuzzBodies(test)

        assertEquals(1, bodies.size)
        assert(bodies[0].contains("Digest.parse(json)"))
        assert(!bodies[0].contains("Other.parse"))
    }
}
