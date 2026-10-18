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

package io.helidon.config.overrides;

import java.util.ArrayList;
import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.config.MetaConfig;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterContext;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigSource;

final class OverrideFilterFactory implements ConfigFilterFactory {
    private final OverrideConfig config;
    private final Config definitions;

    private OverrideFilterFactory(OverrideConfig config, Config definitions, ConfigFilterContext context) {
        this.config = config;
        this.definitions = definitions;
        definitions.onChange(_ -> context.reload());
        context.onChange(new Config.ChangeListener() {
            @Override
            public void onChange(Config changed) {
            }

            @Override
            public void onStop() {
                definitions.context().stopChangeSupport();
            }
        });
    }

    static OverrideFilterFactory create(OverrideConfig config, ConfigFilterContext context) {
        var sources = new ArrayList<ConfigSource>();
        config.configSources().forEach(supplier -> sources.add(Objects.requireNonNull(supplier.get())));
        config.sourceDescriptors().forEach(descriptor -> sources.addAll(MetaConfig.configSource(descriptor)));
        Config definitions;
        if (sources.isEmpty()) {
            definitions = Config.empty();
        } else {
            // Publish definitions before the source callback returns; target reload requests remain asynchronous.
            var builder = Config.builder()
                    .changesExecutor(Runnable::run)
                    .disableEnvironmentVariablesSource()
                    .disableSystemPropertiesSource()
                    .disableFilterServices()
                    .disableValueResolving();
            sources.forEach(builder::addSource);
            definitions = builder.build();
        }
        try {
            return new OverrideFilterFactory(config, definitions, context);
        } catch (RuntimeException | Error e) {
            try {
                definitions.context().stopChangeSupport();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    @Override
    public ConfigFilter create(Config rawConfig) {
        Objects.requireNonNull(rawConfig);
        // Capture the latest known definitions without restarting monitoring or reopening source configuration.
        // After stop, a manual target reload can still use this last known snapshot.
        Config snapshot = definitions.context().last();
        return new OverrideConfigFilter(config, snapshot);
    }
}
