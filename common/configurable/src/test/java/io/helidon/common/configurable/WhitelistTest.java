/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class WhitelistTest {
    static Stream<TestData> data() {
        Config config = Config.create(ConfigSources.classpath("whitelist/whitelist.yaml"));

        return Stream.of(
                new TestData(Whitelist.builder().build(),
                             "By default all should be denied",
                             List.of(false, false, false, false, false)),
                new TestData(Whitelist.builder().allowAll().build(),
                             "All should be allowed",
                             List.of(true, true, true, true, true)),
                new TestData(Whitelist.builder().addAllowed("first").build(),
                             "Allow exact",
                             List.of(true, false, false, false, false)),
                new TestData(Whitelist.builder().allowed(List.of("first", "www.helidon.io")).build(),
                             "Allow exact",
                             List.of(true, false, false, false, true)),
                new TestData(Whitelist.builder().addAllowedPrefix("www.helidon.").build(),
                             "Allow prefix",
                             List.of(false, false, false, false, true)),
                new TestData(Whitelist.builder().addAllowedSuffix(".helidon.io").build(),
                             "Allow suffix",
                             List.of(false, false, false, false, true)),
                new TestData(Whitelist.builder().addAllowed(new EmptyStringPredicate()).build(),
                             "Allow predicate",
                             List.of(false, false, true, false, false)),
                new TestData(Whitelist.builder().addAllowedPattern(Pattern.compile("f.*t")).build(),
                             "Allow pattern",
                             List.of(true, false, false, false, false)),
                new TestData(Whitelist.builder()
                                     .addAllowedPattern(Pattern.compile("f.*t"))
                                     .addAllowedSuffix(".helidon.io")
                                     .build(),
                             "Allow combined",
                             List.of(true, false, false, false, true)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDenied("first")
                                     .build(),
                             "Deny exact",
                             List.of(false, true, true, true, true)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDeniedPrefix("www.helidon.")
                                     .build(),
                             "Deny prefix",
                             List.of(true, true, true, true, false)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDeniedSuffix(".helidon.io")
                                     .build(),
                             "Deny suffix",
                             List.of(true, true, true, true, false)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDenied(new EmptyStringPredicate())
                                     .build(),
                             "Deny exact",
                             List.of(true, true, false, true, true)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDeniedPattern(Pattern.compile("f.*t"))
                                     .build(),
                             "Deny pattern",
                             List.of(false, true, true, true, true)),
                new TestData(Whitelist.builder()
                                     .allowAll()
                                     .addDenied("first")
                                     .addDeniedSuffix(".helidon.io")
                                     .build(),
                             "Deny combined",
                             List.of(false, true, true, true, false)),
                new TestData(Whitelist.builder()
                                     .addAllowedPrefix("f")
                                     .addAllowedSuffix("helidon.io")
                                     .addAllowed(new EmptyStringPredicate())
                                     .addDenied("www.helidon.io")
                                     .addDeniedSuffix("st")
                                     .build(),
                             "Deny and Allow combined",
                             List.of(false, false, true, false, false)),
                new TestData(Whitelist.create(config.get("test-allow-1")),
                             "Config: By default all should be denied",
                             List.of(false, false, false, false, false)),
                new TestData(Whitelist.create(config.get("test-allow-2")),
                             "Config: All should be allowed",
                             List.of(true, true, true, true, true)),
                new TestData(Whitelist.create(config.get("test-allow-3")),
                             "Config: Allow exact",
                             List.of(true, false, false, false, false)),
                new TestData(Whitelist.create(config.get("test-allow-4")),
                             "Config: Allow exact",
                             List.of(true, false, false, false, true)),
                new TestData(Whitelist.create(config.get("test-allow-5")),
                             "Config: Allow prefix",
                             List.of(false, false, false, false, true)),
                new TestData(Whitelist.create(config.get("test-allow-6")),
                             "Config: Allow suffix",
                             List.of(false, false, false, false, true)),
                new TestData(Whitelist.create(config.get("test-allow-7")),
                             "Config: Allow pattern",
                             List.of(true, false, false, false, false)),
                new TestData(Whitelist.create(config.get("test-deny-1")),
                             "Config: Deny exact",
                             List.of(false, true, true, true, true)),
                new TestData(Whitelist.create(config.get("test-deny-2")),
                             "Config: Deny exact list",
                             List.of(false, true, true, true, false)),
                new TestData(Whitelist.create(config.get("test-deny-3")),
                             "Config: Deny prefix",
                             List.of(true, true, true, true, false)),
                new TestData(Whitelist.create(config.get("test-deny-4")),
                             "Config: Deny suffix",
                             List.of(true, true, true, true, false)),
                new TestData(Whitelist.create(config.get("test-deny-5")),
                             "Config: Deny pattern",
                             List.of(false, true, true, true, true)),
                new TestData(Whitelist.create(config.get("test-deny-6")),
                             "Config: Deny combined",
                             List.of(false, true, true, true, false)),
                new TestData(Whitelist.create(config.get("test-combined-1")),
                             "Config: Deny and Allow combined",
                             List.of(false, false, true, false, false))
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    void testDefaultIsDenied(TestData data) {
        Iterator<Boolean> expected = data.expected.iterator();
        assertAll(
                () -> assertThat(data.message(), data.whitelist().test("first"), is(expected.next())),
                () -> assertThat(data.message(), data.whitelist().test("*"), is(expected.next())),
                () -> assertThat(data.message(), data.whitelist().test(""), is(expected.next())),
                () -> assertThat(data.message(), data.whitelist().test("\r\n"), is(expected.next())),
                () -> assertThat(data.message(), data.whitelist().test("www.helidon.io"), is(expected.next()))
        );
    }

    private record TestData(Whitelist whitelist, String message, List<Boolean> expected) {
        @Override
        public String toString() {
            return message + " (" + whitelist + ")";
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