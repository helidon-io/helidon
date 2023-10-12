/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common.configurable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.Map.entry;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AllowListTest {

    private static AllowList allowListForConfigTest;

    @BeforeAll
    static void prepareAllowListForConfigTest() {
        var config = Config.just(
                ConfigSources.create(
                        Map.of("allow.exact", "ex1,ex2",
                               "allow.prefix", "pre1,pre2",
                               "allow.suffix", "post1,post2",
                               "allow.pattern", ".+mid.+,.*thisalso.*,as.*well.*",
                               "deny.exact", "ex2,ex3",
                               "deny.prefix", "pre2",
                               "deny.suffix", "post1",
                               "deny.pattern", ".*also.*"
                        )));

        allowListForConfigTest = AllowList.builder().config(config).build();
    }

    static Stream<TestData> data() {
        Config config = Config.create(ConfigSources.classpath("allowlist/allowlist.yaml"));

        return Stream.of(
                new TestData(AllowList.builder().build(),
                             "By default all should be denied",
                             List.of(false, false, false, false, false)),
                new TestData(AllowList.builder().allowAll(true).build(),
                             "All should be allowed",
                             List.of(true, true, true, true, true)),
                new TestData(AllowList.builder().addAllowed("first").build(),
                             "Allow exact",
                             List.of(true, false, false, false, false)),
                new TestData(AllowList.builder().allowed(List.of("first", "www.helidon.io")).build(),
                             "Allow exact",
                             List.of(true, false, false, false, true)),
                new TestData(AllowList.builder().addAllowedPrefix("www.helidon.").build(),
                             "Allow prefix",
                             List.of(false, false, false, false, true)),
                new TestData(AllowList.builder().addAllowedSuffix(".helidon.io").build(),
                             "Allow suffix",
                             List.of(false, false, false, false, true)),
                new TestData(AllowList.builder().addAllowed(new EmptyStringPredicate()).build(),
                             "Allow predicate",
                             List.of(false, false, true, false, false)),
                new TestData(AllowList.builder().addAllowedPattern(Pattern.compile("f.*t")).build(),
                             "Allow pattern",
                             List.of(true, false, false, false, false)),
                new TestData(AllowList.builder()
                                     .addAllowedPattern(Pattern.compile("f.*t"))
                                     .addAllowedSuffix(".helidon.io")
                                     .build(),
                             "Allow combined",
                             List.of(true, false, false, false, true)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDenied("first")
                                     .build(),
                             "Deny exact",
                             List.of(false, true, true, true, true)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDeniedPrefix("www.helidon.")
                                     .build(),
                             "Deny prefix",
                             List.of(true, true, true, true, false)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDeniedSuffix(".helidon.io")
                                     .build(),
                             "Deny suffix",
                             List.of(true, true, true, true, false)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDenied(new EmptyStringPredicate())
                                     .build(),
                             "Deny exact",
                             List.of(true, true, false, true, true)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDeniedPattern(Pattern.compile("f.*t"))
                                     .build(),
                             "Deny pattern",
                             List.of(false, true, true, true, true)),
                new TestData(AllowList.builder()
                                     .allowAll(true)
                                     .addDenied("first")
                                     .addDeniedSuffix(".helidon.io")
                                     .build(),
                             "Deny combined",
                             List.of(false, true, true, true, false)),
                new TestData(AllowList.builder()
                                     .addAllowedPrefix("f")
                                     .addAllowedSuffix("helidon.io")
                                     .addAllowed(new EmptyStringPredicate())
                                     .addDenied("www.helidon.io")
                                     .addDeniedSuffix("st")
                                     .build(),
                             "Deny and Allow combined",
                             List.of(false, false, true, false, false)),
                new TestData(AllowList.create(config.get("test-allow-1")),
                             "Config: By default all should be denied",
                             List.of(false, false, false, false, false)),
                new TestData(AllowList.create(config.get("test-allow-2")),
                             "Config: All should be allowed",
                             List.of(true, true, true, true, true)),
                new TestData(AllowList.create(config.get("test-allow-3")),
                             "Config: Allow exact",
                             List.of(true, false, false, false, false)),
                new TestData(AllowList.create(config.get("test-allow-4")),
                             "Config: Allow exact",
                             List.of(true, false, false, false, true)),
                new TestData(AllowList.create(config.get("test-allow-5")),
                             "Config: Allow prefix",
                             List.of(false, false, false, false, true)),
                new TestData(AllowList.create(config.get("test-allow-6")),
                             "Config: Allow suffix",
                             List.of(false, false, false, false, true)),
                new TestData(AllowList.create(config.get("test-allow-7")),
                             "Config: Allow pattern",
                             List.of(true, false, false, false, false)),
                new TestData(AllowList.create(config.get("test-allow-8")),
                             "Config: Allow pattern with quoted dot",
                             List.of(false, false, false, false, true)),
                new TestData(AllowList.create(config.get("test-deny-1")),
                             "Config: Deny exact",
                             List.of(false, true, true, true, true)),
                new TestData(AllowList.create(config.get("test-deny-2")),
                             "Config: Deny exact list",
                             List.of(false, true, true, true, false)),
                new TestData(AllowList.create(config.get("test-deny-3")),
                             "Config: Deny prefix",
                             List.of(true, true, true, true, false)),
                new TestData(AllowList.create(config.get("test-deny-4")),
                             "Config: Deny suffix",
                             List.of(true, true, true, true, false)),
                new TestData(AllowList.create(config.get("test-deny-5")),
                             "Config: Deny pattern",
                             List.of(false, true, true, true, true)),
                new TestData(AllowList.create(config.get("test-deny-6")),
                             "Config: Deny combined",
                             List.of(false, true, true, true, false)),
                new TestData(AllowList.create(config.get("test-combined-1")),
                             "Config: Deny and Allow combined",
                             List.of(false, false, true, false, false))
        );
    }

    @Test

    void testPatternQuoting() {
        AllowList trustedProxies = AllowList.builder()
                .addAllowedPattern(Pattern.compile("lb.+\\.mycorp\\.com"))
                .addDenied("lbtest.mycorp.com")
                .build();

        assertThat("Good LB", trustedProxies.test("lb13.mycorp.com"), is(true));
        assertThat("Bad LB", trustedProxies.test("lbtest.mycorp.com"), is(false));
        assertThat("Other LB", trustedProxies.test("other.com"), is(false));
    }

    @Test
    void testPropertiesConfig() {
        Config config = Config.just(ConfigSources.classpath("allowlist/test.properties"));
        AllowList trustedProxies = AllowList.create(config.get("server").get("requested-uri-discovery").get("trusted-proxies"));

        assertThat("Good LB", trustedProxies.test("lb13.mycorp.com"), is(true));
        assertThat("Bad LB", trustedProxies.test("lbtest.mycorp.com"), is(false));
        assertThat("Other LB", trustedProxies.test("other.com"), is(false));
    }

    @ParameterizedTest
    @MethodSource("data")
    void testDefaultIsDenied(TestData data) {
        Iterator<Boolean> expected = data.expected.iterator();
        assertAll(
                () -> assertThat(data.message(), data.allowList().test("first"), is(expected.next())),
                () -> assertThat(data.message(), data.allowList().test("*"), is(expected.next())),
                () -> assertThat(data.message(), data.allowList().test(""), is(expected.next())),
                () -> assertThat(data.message(), data.allowList().test("\r\n"), is(expected.next())),
                () -> assertThat(data.message(), data.allowList().test("www.helidon.io"), is(expected.next()))
        );
    }

    @ParameterizedTest
    @MethodSource("configTestData")
    void testConfig(Map.Entry<String, String> entry) {

        assertThat("Test of " + entry.getKey() + " with various settings",
                   allowListForConfigTest.test(entry.getKey()),
                   is(entry.getValue()));
    }

    @Test
    void testAllowAllConfig() {

        var config = Config.just(ConfigSources.create(Map.of("allow.all", "true")));
        AllowList allowList = AllowList.builder().config(config).build();

        for (String s : List.of("a", "b", "anything")) {
            assertThat("Test of " + s + " with allow.all", allowList.test(s), is(true));
        }
    }

    static Stream<Map.Entry<String, Boolean>> configTestData() {
        return Map.ofEntries(entry("ex1", true),
                                  entry("ex2", false),
                                  entry("ex3", false),
                                  entry("ex4", false),
                                  entry("ex", false),
                                  entry("pre1A", true),
                                  entry("Apre1", false),
                                  entry("pre1", true),
                                  entry("pre2A", false),
                                  entry("Bpost1", false),
                                  entry("Bpost2", true),
                                  entry("Bpost", false),
                                  entry("Bpost3", false),
                                  entry("mid", false),
                                  entry("xmid", false),
                                  entry("midy", false),
                                  entry("xmidx", true),
                                  entry("longmidlong", true),
                                  entry("thisalso", false),
                                  entry("aswellasthisone", true))
                .entrySet().stream();
    }

    private record TestData(AllowList allowList, String message, List<Boolean> expected) {
        @Override
        public String toString() {
            return message + " (" + allowList + ")";
        }
    }

    private static final class EmptyStringPredicate implements Predicate<String> {
        @Override
        public boolean test(String s) {
            return s.isEmpty();
        }

        @Override
        public String toString() {
            return "TestEmptyPredicate";
        }
    }
}