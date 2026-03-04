/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.data.sql.testing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigValue;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.service.registry.Service;

/**
 * Services factory for the tests.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(1000)
public class TestConfigFactory implements Service.ServicesFactory<Config> {

    // Hold config in static context
    private static volatile Config config;

    private final ConfigDelegate configDelegate;

    TestConfigFactory() {
        configDelegate = new ConfigDelegate(this);
    }

    @Override
    public List<Service.QualifiedInstance<Config>> services() {
        return List.of(Service.QualifiedInstance.create(configDelegate));
    }

    private Config config() {
        if (config == null) {
            config = Config.create();
        }
        return config;
    }

    /**
     * Set the config instance to use.
     *
     * @param config config instance to use
     */
    public static void config(Config config) {
        TestConfigFactory.config = config;
    }

    /**
     * Helidon config delegate for {@link Config} in {@link TestConfigFactory}.
     */
    public static class ConfigDelegate implements Config {

        private final TestConfigFactory factory;

        ConfigDelegate(TestConfigFactory factory) {
            this.factory = factory;
        }

        /**
         * Returns {@link Config} from {@link TestConfigFactory}.
         *
         * @param config the {@link Config} insgtance
         */
        public void config(Config config) {
            TestConfigFactory.config(config);
        }

        @Override
        public Instant timestamp() {
            return factory.config().timestamp();
        }

        @Override
        public Type type() {
            return factory.config().type();
        }

        @Override
        public Key key() {
            return factory.config().key();
        }

        @Override
        public Config get(Key key) {
            return factory.config().get(key);
        }

        @Override
        public Config root() {
            return factory.config().root();
        }

        @Override
        public Stream<Config> traverse(Predicate<Config> predicate) {
            return factory.config().traverse(predicate);
        }

        @Override
        public Config detach() throws ConfigException {
            return factory.config().detach();
        }

        @Override
        public boolean hasValue() {
            return factory.config().hasValue();
        }

        @Override
        public <T> ConfigValue<T> as(Class<T> aClass) {
            return factory.config().as(aClass);
        }

        @Override
        public <T> ConfigValue<T> as(GenericType<T> genericType) {
            return factory.config().as(genericType);
        }

        @Override
        public <T> ConfigValue<T> as(Function<Config, T> mapper) {
            return factory.config().as(mapper);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> aClass) throws ConfigException {
            return factory.config().asList(aClass);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Function<Config, T> mapper) throws ConfigMappingException {
            return factory.config().asList(mapper);
        }

        @Override
        public ConfigValue<List<Config>> asNodeList() throws ConfigException {
            return factory.config().asNodeList();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws ConfigException {
            return factory.config().asMap();
        }

        @Override
        public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
            return factory.config().convert(type, value);
        }

        @Override
        public ConfigMapper mapper() {
            return factory.config().mapper();
        }
    }
}
