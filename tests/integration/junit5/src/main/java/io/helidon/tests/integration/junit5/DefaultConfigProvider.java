/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.reflect.Type;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tests.integration.junit5.spi.ConfigProvider;

/**
 * Default {@link TestConfig} provider.
 * Reads config file specified by the annotation and optionally stores it
 * into {@link SuiteContext#storage()} using {@link TestConfig#key()}.
 * This class also serves as {@link SuiteResolver} for stored {@link Config}.
 */
public class DefaultConfigProvider implements ConfigProvider, ConfigUpdate, SuiteResolver {

    private String fileName;
    private SuiteContext suiteContext;
    private Config config;
    private Config.Builder builder;

    public DefaultConfigProvider() {
        fileName = null;
        suiteContext = null;
        config = null;
        builder = null;
    }

    @Override
    public void suiteContext(SuiteContext suiteContext) {
        this.suiteContext = suiteContext;
    }

    @Override
    public void file(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void setup() {
        builder = Config.builder().addSource(ConfigSources.classpath(fileName));
    }

    @Override
    public Config.Builder builder() {
        return builder;
    }

    @Override
    public void start() {
        config = builder.build();
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void config(Config config) {
        this.config = config;
    }

    @Override
    public boolean supportsParameter(Type type) {
        return Config.class.isAssignableFrom((Class<?>) type)
                || ConfigUpdate.class.isAssignableFrom((Class<?>) type);
    }

    @Override
    public Object resolveParameter(Type type) {
        if (Config.class.isAssignableFrom((Class<?>)type)) {
            return config;
        } else if (ConfigUpdate.class.isAssignableFrom((Class<?>) type)) {
            return this;
        }

        throw new IllegalArgumentException(String.format("Cannot resolve parameter Type %s", type.getTypeName()));
    }

}
