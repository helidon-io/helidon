/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.testing.testng;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.AddConfigSource;
import io.helidon.microprofile.testing.Configuration;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;

@HelidonTest
@AddConfig(key = "foo", value = "addConfig")
@AddConfigBlock(value = """
        foo=configBlock
        """)
@Configuration(configSources = "ordinal-default.properties")
public class TestConfigSourceOrderingDefault {

    private static final List<Ordering> ORDERINGS = List.of(
            new Ordering(1000, "addConfig"),
            new Ordering(900, "configBlock"),
            new Ordering(800, "configSource"),
            new Ordering(700, "configuration"),
            new Ordering(400, "systemProperty"),
            new Ordering(300, "environmentProperty"));

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    private Config config;

    @AddConfigSource
    static ConfigSource config() {
        return new CustomConfigSource();
    }

    @Test
    void testConfigSourceOrdering() {
        Iterator<Ordering> ordering = ORDERINGS.iterator();
        Iterable<ConfigSource> configSources = config.getConfigSources();

        assertThat(configSources, iterableWithSize(6));

        for (ConfigSource configSource : configSources) {
            Ordering it = ordering.next();
            assertThat(it.ordinal(), is(configSource.getOrdinal()));
            assertThat(it.value(), is(configSource.getValue("foo")));
        }
    }

    static class CustomConfigSource implements ConfigSource {
        @Override
        public Set<String> getPropertyNames() {
            return Set.of();
        }

        @Override
        public String getValue(String propertyName) {
            return "foo".equals(propertyName) ? "configSource" : null;
        }

        @Override
        public String getName() {
            return CustomConfigSource.class.getSimpleName();
        }
    }

    record Ordering(int ordinal, String value) {
    }
}
