/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.config;

import java.time.YearMonth;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.MissingValueException;
import io.helidon.microprofile.config.Converters.Ctor;
import io.helidon.microprofile.config.Converters.Of;
import io.helidon.microprofile.config.Converters.Parse;
import io.helidon.microprofile.config.Converters.ValueOf;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test for {@link MpConfig}.
 */
class MpConfigTest {
    private static MpConfig mpConfig;

    @BeforeAll
    static void initClass() {
        Map<String, String> properties = CollectionsHelper.mapOf(
                "boolean", "true",
                "array", "a,b,c,d",
                "int", "14"
        );

        ConfigSource myConfigSource = new ConfigSource() {
            @Override
            public Map<String, String> getProperties() {
                return properties;
            }

            @Override
            public String getValue(String propertyName) {
                return properties.get(propertyName);
            }

            @Override
            public String getName() {
                return "helidon:unit-test";
            }
        };

        mpConfig = (MpConfig) new MpConfigBuilder()
                .addDefaultSources()
                .withSources(myConfigSource)
                .build();
    }

    @Test
    void testBooleans() {
        MpConfig mpConfig = (MpConfig) new MpConfigBuilder()
                .build();

        assertAll("Boolean conversions",
                  () -> assertThat("true", mpConfig.convert(Boolean.class, "true"), is(true)),
                  () -> assertThat("false", mpConfig.convert(Boolean.class, "false"), is(false)),
                  () -> assertThat("true, primitive", mpConfig.convert(boolean.class, "true"), is(true)),
                  () -> assertThat("false, primitive", mpConfig.convert(boolean.class, "false"), is(false)),
                  () -> assertThat("on", mpConfig.convert(boolean.class, "on"), is(true)),
                  () -> assertThat("1", mpConfig.convert(boolean.class, "1"), is(true)),
                  () -> assertThat("true, array", mpConfig.convert(Boolean[].class, "true, false"),
                                   arrayContaining(true, false))
                  );
    }

    @Test
    void testStringTypes() {
        assertAll("Various property types",
                  () -> assertThat("As string list",
                                   mpConfig.asList("array", String.class),
                                   hasItems("a", "b", "c", "d")),
                  () -> assertThat("As string array",
                                   mpConfig.getValue("array", String[].class),
                                   arrayContaining("a", "b", "c", "d")),
                  () -> assertThat("As string array",
                                   mpConfig.convert(String[].class, "a,b\\,,c,d"),
                                   arrayContaining("a", "b,", "c", "d")),
                  () -> assertThat("As set",
                                   mpConfig.asSet("array", String.class),
                                   hasItems("a", "b", "c", "d")),
                  () -> assertThat("As string",
                                   mpConfig.getValue("array", String.class),
                                   is("a,b,c,d"))
        );
    }

    @Test
    void testSystemPropertyAndEnvironmentVariableNamesArePresent() {
        final Iterable<String> propertyNames = mpConfig.getPropertyNames();
        assertThat("Set", propertyNames, instanceOf(Set.class));
        assertThat("System properties", ((Set<String>) propertyNames).contains("java.home"));
        assumeTrue(System.getenv("PATH") != null);
        assertThat("Environment variables", ((Set<String>) propertyNames).contains("PATH"));
    }

    @Test
    public void testGetValueOfNonExistentValueShouldThrowNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> mpConfig.getValue("nonexistent.property", String.class));
    }

    @Test
    public void testFindValueOfNonExistentValueShouldThrowMissingValueException() {
        assertThrows(MissingValueException.class, () -> mpConfig.findValue("nonexistent.property", String.class));
    }

    @Test
    public void testImplicitConversion() {
        MpConfig mpConfig = (MpConfig) new MpConfigBuilder()
            .build();

        assertAll("Implicit conversions",
                  () -> assertThat("of", mpConfig.convert(Of.class, "foo"), is(Of.of("foo"))),
                  () -> assertThat("valueOf", mpConfig.convert(ValueOf.class, "foo"), is(ValueOf.valueOf("foo"))),
                  () -> assertThat("parse", mpConfig.convert(Parse.class, "foo"), is(Parse.parse("foo"))),
                  () -> assertThat("ctor", mpConfig.convert(Ctor.class, "foo"), is(new Ctor("foo"))),
                  () -> assertThat("year month", mpConfig.convert(YearMonth.class, "2019-03"), is(YearMonth.parse("2019-03")))
        );
    }
}
