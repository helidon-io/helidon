/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import io.helidon.config.Config;

class RegistrySettingsImpl implements RegistrySettings {

    static Builder builder() {
        return new RegistrySettingsImpl.Builder();
    }

    private final boolean isEnabled;
    private final RegistryFilterSettings registryFilterSettings;
    private final Config config;

    protected RegistrySettingsImpl(Builder builder) {
        config = builder.config;
        isEnabled = builder.isEnabled;
        registryFilterSettings = builder.registryFilterSettingsBuilder.build();
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public boolean isMetricEnabled(String dottedName) {
        return isEnabled && registryFilterSettings.passes(dottedName);
    }

    @Override
    public String value(String key) {
        return config != null ? config.get(key).asString().orElse(null)
                : null;
    }

    static class Builder implements RegistrySettings.Builder {

        private boolean isEnabled = true;
        private Config config;

        private RegistryFilterSettings.Builder registryFilterSettingsBuilder = RegistryFilterSettings.builder();

        @Override
        public RegistrySettingsImpl build() {
            return new RegistrySettingsImpl(this);
        }

        @Override
        public RegistrySettings.Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public RegistrySettings.Builder filterSettings(RegistryFilterSettings.Builder registryFilterSettingsBuilder) {
            this.registryFilterSettingsBuilder = registryFilterSettingsBuilder;
            return this;
        }

        @Override
        public RegistrySettings.Builder config(Config config) {
            this.config = config;
            config.get(Builder.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);

            config.get(Builder.FILTER_CONFIG_KEY)
                    .as(RegistryFilterSettings.Builder::create)
                    .ifPresent(this::filterSettings);
            return this;
        }

        @Override
        public boolean isEnabled() {
            return isEnabled;
        }
    }
}
