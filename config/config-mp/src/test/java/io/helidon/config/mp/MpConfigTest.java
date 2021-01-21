/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.config.mp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test MicroProfile config implementation.
 */
public class MpConfigTest {
    private static Config config;
    private static io.helidon.config.Config emptyConfig;

    @BeforeAll
    static void initClass() {
        config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp-1", "mp-value-1",
                                                           "mp-2", "mp-value-2",
                                                           "app.storageEnabled", "false",
                                                           "mp-array", "a,b,c",
                                                           "mp-list.0", "1",
                                                           "mp-list.1", "2",
                                                           "mp-list.2", "3")),
                             MpConfigSources.create(Map.of("app.storageEnabled", "true",
                                                           ConfigSource.CONFIG_ORDINAL, "1000")))
                .build();

        // we need to ensure empty config is initialized before running other tests,
        // as this messes up the mapping service counter
        emptyConfig = io.helidon.config.Config.empty();
    }

    @Test
    void testConfigSources() {
        Iterable<ConfigSource> configSources = config.getConfigSources();
        List<ConfigSource> asList = new ArrayList<>();
        for (ConfigSource configSource : configSources) {
            asList.add(configSource);
        }

        assertThat(asList, hasSize(2));

        assertThat(asList.get(0), instanceOf(MpMapSource.class));
        assertThat(asList.get(1), instanceOf(MpMapSource.class));

        // first is the one with higher config ordinal
        ConfigSource map = asList.get(0);
        assertThat(map.getValue("app.storageEnabled"), is("true"));

        map = asList.get(1);
        assertThat(map.getValue("mp-1"), is("mp-value-1"));
        assertThat(map.getValue("mp-2"), is("mp-value-2"));
        assertThat(map.getValue("app.storageEnabled"), is("false"));
    }

    @Test
    void testOptionalValue() {
        assertThat(config.getOptionalValue("app.storageEnabled", Boolean.class), is(Optional.of(true)));
        assertThat(config.getOptionalValue("mp-1", String.class), is(Optional.of("mp-value-1")));
    }

    @Test
    void testStringArray() {
        String[] values = config.getValue("mp-array", String[].class);
        assertThat(values, arrayContaining("a", "b", "c"));
    }

    @Test
    void testIntArray() {
        Integer[] values = config.getValue("mp-list", Integer[].class);
        assertThat(values, arrayContaining(1, 2, 3));
    }

    @Test
    void mutableTest() {
        // THIS MUST WORK - the spec says the sources can be mutable and config must use the latest values
        var mutable = new MutableConfigSource();

        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(mutable)
                .build();

        String value = config.getValue("key", String.class);
        assertThat(value, is("initial"));

        String updated = "updated";
        mutable.set(updated);
        value = config.getValue("key", String.class);
        assertThat(value, is(updated));
    }

    @Test
    void arrayTest() {
        MutableConfigSource cs = new MutableConfigSource();
        cs.set("large:cheese\\,mushroom,medium:chicken,small:pepperoni");
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withConverter(Pizza.class, 10, value -> {
                    String[] parts = value.split(":");
                    if (parts.length == 2) {
                        String size = parts[0];
                        String flavor = parts[1];
                        return new Pizza(flavor, size);
                    }

                    return null;
                })
                .withSources(cs)
                .build();

        Pizza[] value = config.getValue("key",
                                        Pizza[].class);

        assertThat(value, notNullValue());
        assertThat(value, arrayWithSize(3));
        assertThat(value, is(new Pizza[] {
                new Pizza("cheese,mushroom", "large"),
                new Pizza("chicken", "medium"),
                new Pizza("pepperoni", "small")
        }));
    }

    /**
     * Ensure mapping services are still enabled when converting configuration from MP to SE.
     *
     * @see "https://github.com/oracle/helidon/issues/1802"
     */
    @Test
    public void testMpToHelidonConfigMappingServicesNotDisabled() {
        var mutable = new MutableConfigSource();

        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(mutable)
                .build();

        TestMapperProvider.reset(); // reset count so we can properly assert the converted config creates the mappers

        MpConfig.toHelidonConfig(config);
        assertThat(TestMapperProvider.getCreationCount(), is(1));
    }

    // Github issue #2206
    @Test
    void testFailing() {
        Config mpConfig = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(io.helidon.config.Config.builder()
                                                            .sources(ConfigSources.create(Map.of("foo", "bar")))
                                                            .build()))
                .build();

        assertThat(mpConfig.getValue("foo", String.class), is("bar"));
    }

    private static class MutableConfigSource implements ConfigSource {
        private final AtomicReference<String> value = new AtomicReference<>("initial");

        @Override
        public Set<String> getPropertyNames() {
            return Set.of("key");
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of("key", value.get());
        }

        @SuppressWarnings("ReturnOfNull")
        @Override
        public String getValue(String propertyName) {
            if ("key".equals(propertyName)) {
                return value.get();
            }
            // this is required by the specification (null returns if not found)
            return null;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        private void set(String value) {
            this.value.set(value);
        }
    }

    public static class Pizza {
        private final String flavor;
        private final String size;

        private Pizza(String flavour, String size) {
            this.flavor = flavour;
            this.size = size;
        }

        @Override
        public String toString() {
            return flavor + ":" + size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Pizza pizza = (Pizza) o;
            return flavor.equals(pizza.flavor) &&
                    size.equals(pizza.size);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flavor, size);
        }
    }

    public static final class TestMapperProvider implements ConfigMapperProvider {
        private static final AtomicInteger counter = new AtomicInteger();

        public TestMapperProvider() {
            counter.incrementAndGet();
        }

        @Override
        public Map<Class<?>, Function<io.helidon.config.Config, ?>> mappers() {
            return Map.of();
        }

        @Override
        public Map<GenericType<?>, BiFunction<io.helidon.config.Config, ConfigMapper, ?>> genericTypeMappers() {
            return Map.of();
        }

        @Override
        public <T> Optional<Function<io.helidon.config.Config, T>> mapper(final Class<T> type) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<BiFunction<io.helidon.config.Config, ConfigMapper, T>> mapper(final GenericType<T> type) {
            return Optional.empty();
        }

        public static int getCreationCount() {
            return counter.get();
        }

        public static void reset() {
            counter.set(0);
        }
    }

    @Test
    void testEnvVar() {
        ConfigProviderResolver instance = ConfigProviderResolver.instance();
        ClassLoader myCl = Thread.currentThread().getContextClassLoader();
        Config current = ConfigProvider.getConfig(myCl);

        try {
            instance.registerConfig(instance.getBuilder()
                                            .withSources(MpConfigSources.environmentVariables())
                                            .build(),
                                    myCl);
            Config myConfig = instance.getConfig(myCl);
            // this must not throw an exception - path should be on any environment
            // and the MP env var processing should make it available
            String fooBar = myConfig.getValue("foo.bar", String.class);
            assertThat(fooBar, is("mapped-env-value"));

            io.helidon.config.Config helidonConfig = (io.helidon.config.Config) myConfig;
            // should work if we use it as SE as well
            fooBar = helidonConfig.get("foo.bar").asString().get();
            assertThat(fooBar, is("mapped-env-value"));
        } finally {
            instance.registerConfig(current, myCl);
        }
    }
}

