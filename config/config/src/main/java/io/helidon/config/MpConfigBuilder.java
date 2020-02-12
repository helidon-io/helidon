/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Configuration builder.
 */
public class MpConfigBuilder implements ConfigBuilder {
    private final BuilderImpl delegate = new BuilderImpl();

    MpConfigBuilder() {
        delegate.disableSystemPropertiesSource();
        delegate.disableEnvironmentVariablesSource();
        delegate.disableSourceServices();
        delegate.disableMpMapperServices();
    }

    @Override
    public ConfigBuilder addDefaultSources() {
        delegate.mpAddDefaultSources();
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredSources() {
        delegate.mpAddDiscoveredSources();
        return this;
    }

    @Override
    public ConfigBuilder addDiscoveredConverters() {
        delegate.mpAddDiscoveredConverters();
        return this;
    }

    @Override
    public ConfigBuilder forClassLoader(ClassLoader loader) {
        delegate.mpForClassLoader(loader);
        return this;
    }

    @Override
    public ConfigBuilder withSources(ConfigSource... sources) {
        delegate.mpWithSources(sources);
        return this;
    }

    @Override
    public <T> ConfigBuilder withConverter(Class<T> aClass, int ordinal, Converter<T> converter) {
        delegate.mpWithConverter(aClass, ordinal, converter);
        return this;
    }

    @Override
    public ConfigBuilder withConverters(Converter<?>... converters) {
        delegate.mpWithConverters(converters);
        return this;
    }

    @Override
    public Config build() {
        return delegate.build();
    }

    ConfigBuilder metaConfig(io.helidon.config.Config metaConfig) {
        delegate.config(metaConfig);
        return this;
    }
}
