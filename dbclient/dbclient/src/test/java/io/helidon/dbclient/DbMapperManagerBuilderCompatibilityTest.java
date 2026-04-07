/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.dbclient.spi.DbMapperProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DbMapperManagerBuilderCompatibilityTest {

    @Test
    void testDbMapperManagerBuilderPublicApiShape() throws Exception {
        Method addMapperProvider = DbMapperManager.Builder.class.getMethod("addMapperProvider", DbMapperProvider.class);
        Method addMapperProviderWeighted = DbMapperManager.Builder.class.getMethod("addMapperProvider",
                                                                                   DbMapperProvider.class,
                                                                                   int.class);
        Method build = DbMapperManager.Builder.class.getMethod("build");

        assertThat(addMapperProvider.getReturnType().getName(), is(DbMapperManager.Builder.class.getName()));
        assertThat(addMapperProviderWeighted.getReturnType().getName(), is(DbMapperManager.Builder.class.getName()));
        assertThat(build.getReturnType().getName(), is(DbMapperManager.class.getName()));
    }

    @Test
    void testDbMapperManagerWeightedProviders() {
        DbMapperManager manager = DbMapperManager.builder()
                .addMapperProvider(new NamedProvider("low"), 100)
                .addMapperProvider(new NamedProvider("high"), 1000)
                .build();

        assertThat(manager.toNamedParameters(new TestMappedType("value"), TestMappedType.class),
                   is(Map.of("provider", "high", "value", "value")));
    }

    @Test
    void testDbMapperManagerCreateFromCustomServiceLoader() {
        HelidonServiceLoader<DbMapperProvider> serviceLoader = HelidonServiceLoader.builder(ServiceLoader.load(DbMapperProvider.class))
                .useSystemServiceLoader(false)
                .addService(new NamedProvider("custom"))
                .build();

        DbMapperManager manager = DbMapperManager.create(serviceLoader);

        assertThat(manager.toNamedParameters(new TestMappedType("value"), TestMappedType.class),
                   is(Map.of("provider", "custom", "value", "value")));
    }

    private record TestMappedType(String value) {
    }

    private static final class NamedProvider implements DbMapperProvider {
        private final String name;

        private NamedProvider(String name) {
            this.name = name;
        }

        @Override
        public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
            if (type.equals(TestMappedType.class)) {
                @SuppressWarnings("unchecked")
                DbMapper<T> mapper = (DbMapper<T>) new DbMapper<TestMappedType>() {
                    @Override
                    public TestMappedType read(DbRow row) {
                        return new TestMappedType(name);
                    }

                    @Override
                    public Map<String, ?> toNamedParameters(TestMappedType value) {
                        return Map.of("provider", name, "value", value.value());
                    }

                    @Override
                    public List<?> toIndexedParameters(TestMappedType value) {
                        return List.of(name, value.value());
                    }
                };
                return Optional.of(mapper);
            }
            return Optional.empty();
        }
    }
}
