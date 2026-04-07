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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.Mappers;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.dbclient.spi.DbClientBuilder;
import io.helidon.dbclient.spi.DbClientProvider;
import io.helidon.dbclient.spi.DbMapperProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

class DbClientBuilderCompatibilityTest {

    @BeforeEach
    void resetServiceProvider() {
        TestDbClientServiceProvider.reset();
    }

    @Test
    void testDbClientBuilderDelegatesAndLoadsConfiguredServices() {
        RecordingDbClientProvider provider = new RecordingDbClientProvider();
        Config config = Config.builder()
                .addSource(ConfigSources.create(Map.of("services.test-service-provider.enabled", "true")))
                .build();
        DbStatements statements = name -> "statement-for-" + name;
        DbMapperProvider mapperProvider = new DbMapperProvider() {
            @Override
            public <T> Optional<DbMapper<T>> mapper(Class<T> type) {
                return Optional.empty();
            }
        };
        Mappers mapperManager = Mappers.builder()
                .useBuiltInMappers(false)
                .build();
        DbClientService explicitService = context -> context;
        DbClientService suppliedService = context -> context;

        DbClient client = DbClient.builder(provider)
                .config(config)
                .statements(statements)
                .mapperProvider(mapperProvider)
                .mapperManager(mapperManager)
                .addService(explicitService)
                .addService(() -> suppliedService)
                .build();

        RecordingDbClientBuilder builder = provider.builder;
        assertThat(builder.config, sameInstance(config));
        assertThat(builder.statements, sameInstance(statements));
        assertThat(builder.mapperManager, sameInstance(mapperManager));
        assertThat(builder.mapperProviders, hasItem(mapperProvider));
        assertThat(builder.services, hasItems(explicitService, suppliedService, TestDbClientServiceProvider.SERVICE));
        assertThat(TestDbClientServiceProvider.createCalls, is(1));
        assertThat(client, sameInstance(builder.client));
    }

    @Test
    void testDbClientBuilderBaseStateAndLazyDefaults() {
        DbStatements statements = name -> "stmt-" + name;
        DbClientService service = context -> context;
        TestMappedType mappedValue = new TestMappedType("gizmo");
        DbMapper<TestMappedType> mapper = new DbMapper<>() {
            @Override
            public TestMappedType read(DbRow row) {
                return mappedValue;
            }

            @Override
            public Map<String, ?> toNamedParameters(TestMappedType value) {
                return Map.of("name", value.name());
            }

            @Override
            public List<?> toIndexedParameters(TestMappedType value) {
                return List.of(value.name());
            }
        };

        TestBaseBuilder builder = new TestBaseBuilder()
                .url("jdbc:test")
                .username("scott")
                .password("tiger")
                .missingMapParametersAsNull(true)
                .statements(statements)
                .addMapper(mapper, TestMappedType.class)
                .addService(service);

        DbClient client = builder.build();

        assertThat(client, sameInstance(builder.client));
        assertThat(builder.url(), is("jdbc:test"));
        assertThat(builder.username(), is("scott"));
        assertThat(builder.password(), is("tiger"));
        assertThat(builder.missingMapParametersAsNull(), is(true));
        assertThat(builder.statements(), sameInstance(statements));
        assertThat(builder.clientServices(), contains(service));
        assertThat(builder.mapperManager(), notNullValue());
        assertThat(builder.dbMapperManager(), notNullValue());
        assertThat(builder.dbMapperManager().toNamedParameters(mappedValue, TestMappedType.class),
                   is(Map.of("name", "gizmo")));
    }

    @Test
    void testDbClientBuilderPublicApiShape() throws Exception {
        Method config = DbClient.Builder.class.getMethod("config", Config.class);
        Method statements = DbClient.Builder.class.getMethod("statements", DbStatements.class);
        Method mapperProvider = DbClient.Builder.class.getMethod("mapperProvider", DbMapperProvider.class);
        Method mapperManager = DbClient.Builder.class.getMethod("mapperManager", Mappers.class);
        Method addService = DbClient.Builder.class.getMethod("addService", DbClientService.class);
        Method addServiceSupplier = DbClient.Builder.class.getMethod("addService", Supplier.class);
        Method build = DbClient.Builder.class.getMethod("build");

        assertThat(config.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(statements.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(mapperProvider.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(mapperManager.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(addService.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(addServiceSupplier.getReturnType().getName(), is(DbClient.Builder.class.getName()));
        assertThat(build.getReturnType().getName(), is(DbClient.class.getName()));
    }

    private static final class RecordingDbClientProvider implements DbClientProvider {
        private final RecordingDbClientBuilder builder = new RecordingDbClientBuilder();

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public DbClientBuilder<?> builder() {
            return builder;
        }
    }

    private static final class RecordingDbClientBuilder implements DbClientBuilder<RecordingDbClientBuilder> {
        private final DbClient client = new TestDbClient();
        private final List<DbMapperProvider> mapperProviders = new ArrayList<>();
        private final List<DbClientService> services = new ArrayList<>();
        private Config config;
        private DbStatements statements;
        private Mappers mapperManager;

        @Override
        public RecordingDbClientBuilder config(Config config) {
            this.config = config;
            return this;
        }

        @Override
        public RecordingDbClientBuilder url(String url) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder username(String username) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder password(String password) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder missingMapParametersAsNull(boolean missingMapParametersAsNull) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder statements(DbStatements statements) {
            this.statements = statements;
            return this;
        }

        @Override
        public RecordingDbClientBuilder addMapperProvider(DbMapperProvider provider) {
            mapperProviders.add(provider);
            return this;
        }

        @Override
        public <TYPE> RecordingDbClientBuilder addMapper(DbMapper<TYPE> dbMapper, Class<TYPE> mappedClass) {
            return this;
        }

        @Override
        public <TYPE> RecordingDbClientBuilder addMapper(DbMapper<TYPE> dbMapper, GenericType<TYPE> mappedType) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder mapperManager(Mappers manager) {
            this.mapperManager = manager;
            return this;
        }

        @Override
        public RecordingDbClientBuilder dbMapperManager(DbMapperManager manager) {
            return this;
        }

        @Override
        public RecordingDbClientBuilder addService(DbClientService clientService) {
            services.add(clientService);
            return this;
        }

        @Override
        public DbClient build() {
            return client;
        }
    }

    private static final class TestBaseBuilder extends DbClientBuilderBase<TestBaseBuilder> {
        private final DbClient client = new TestDbClient();

        @Override
        protected DbClient doBuild() {
            return client;
        }
    }

    private record TestMappedType(String name) {
    }

    private static final class TestDbClient implements DbClient {
        @Override
        public String dbType() {
            return "test";
        }

        @Override
        public DbExecute execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DbTransaction transaction() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <C> C unwrap(Class<C> cls) {
            return Optional.of(this)
                    .filter(cls::isInstance)
                    .map(cls::cast)
                    .orElseThrow(() -> new UnsupportedOperationException(cls.getName()));
        }

        @Override
        public void close() {
        }
    }
}
