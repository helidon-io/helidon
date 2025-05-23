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
package io.helidon.data.sql.testing;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
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
        public Key key() {
            return factory.config().key();
        }

        @Override
        public Config get(String s) throws ConfigException {
            return factory.config().get(s);
        }

        @Override
        public Config root() {
            return factory.config().root();
        }

        @Override
        public Config detach() throws ConfigException {
            return factory.config().detach();
        }

        @Override
        public boolean exists() {
            return factory.config().exists();
        }

        @Override
        public boolean isLeaf() {
            return factory.config().isLeaf();
        }

        @Override
        public boolean isObject() {
            return factory.config().isObject();
        }

        @Override
        public boolean isList() {
            return factory.config().isList();
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
        public <T> ConfigValue<T> map(Function<Config, T> function) {
            return factory.config().map(function);
        }

        @Override
        public <T> ConfigValue<List<T>> asList(Class<T> aClass) throws ConfigException {
            return factory.config().asList(aClass);
        }

        @Override
        public <T> ConfigValue<List<T>> mapList(Function<Config, T> function) throws ConfigException {
            return factory.config().mapList(function);
        }

        @Override
        public <C extends Config> ConfigValue<List<C>> asNodeList() throws ConfigException {
            return factory.config().asNodeList();
        }

        @Override
        public ConfigValue<Map<String, String>> asMap() throws ConfigException {
            return factory.config().asMap();
        }
    }

}
