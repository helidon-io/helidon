/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 *
 */

package io.helidon.config;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.config.spi.ConfigSource;

public class BuilderImpl implements Config.Builder {
    @Override
    public Config.Builder disableSourceServices() {
        return null;
    }

    @Override
    public Config.Builder sources(List<Supplier<? extends ConfigSource>> configSources) {
        return null;
    }

    @Override
    public Config.Builder addSource(ConfigSource source) {
        return null;
    }

    @Override
    public Config.Builder overrides(Supplier<OverrideSource> overridingSource) {
        return null;
    }

    @Override
    public Config.Builder disableKeyResolving() {
        return null;
    }

    @Override
    public Config.Builder disableValueResolving() {
        return null;
    }

    @Override
    public Config.Builder disableEnvironmentVariablesSource() {
        return null;
    }

    @Override
    public Config.Builder disableSystemPropertiesSource() {
        return null;
    }

    @Override
    public <T> Config.Builder addMapper(Class<T> type, Function<Config, T> mapper) {
        return null;
    }

    @Override
    public <T> Config.Builder addMapper(GenericType<T> type, Function<Config, T> mapper) {
        return null;
    }

    @Override
    public <T> Config.Builder addStringMapper(Class<T> type, Function<String, T> mapper) {
        return null;
    }

    @Override
    public Config.Builder addMapper(ConfigMapperProvider configMapperProvider) {
        return null;
    }

    @Override
    public Config.Builder disableMapperServices() {
        return null;
    }

    @Override
    public Config.Builder addParser(ConfigParser configParser) {
        return null;
    }

    @Override
    public Config.Builder disableParserServices() {
        return null;
    }

    @Override
    public Config.Builder addFilter(ConfigFilter configFilter) {
        return null;
    }

    @Override
    public Config.Builder addFilter(Function<Config, ConfigFilter> configFilterProvider) {
        return null;
    }

    @Override
    public Config.Builder addFilter(Supplier<Function<Config, ConfigFilter>> configFilterSupplier) {
        return null;
    }

    @Override
    public Config.Builder disableFilterServices() {
        return null;
    }

    @Override
    public Config.Builder disableCaching() {
        return null;
    }

    @Override
    public Config.Builder changesExecutor(Executor changesExecutor) {
        return null;
    }

    @Override
    public Config.Builder changesMaxBuffer(int changesMaxBuffer) {
        return null;
    }

    @Override
    public Config build() {
        return null;
    }

    @Override
    public Config.Builder config(Config metaConfig) {
        return null;
    }

    /**
     * Holds single instance of empty Config.
     */
    static final class EmptyConfigHolder {
        private EmptyConfigHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        static final Config EMPTY = new BuilderImpl()
                // the empty config source is needed, so we do not look for meta config or default
                // config sources
                .addSource(ConfigSources.empty())
                .addOverride(OverrideSources.empty())
                .disableSourceServices()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableFilterServices()
                .build();

    }
}
