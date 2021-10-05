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

class ComponentMetricsSettingsImpl implements ComponentMetricsSettings {

    private final boolean isEnabled;

    private ComponentMetricsSettingsImpl(Builder builder) {
        isEnabled = builder.isEnabled;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    static class Builder implements ComponentMetricsSettings.Builder {

        private boolean isEnabled = true;

        @Override
        public ComponentMetricsSettings build() {
            return new ComponentMetricsSettingsImpl(this);
        }

        @Override
        public ComponentMetricsSettings.Builder enabled(boolean value) {
            this.isEnabled = value;
            return this;
        }

        @Override
        public ComponentMetricsSettings.Builder config(Config config) {
            config.get(ComponentMetricsSettings.Builder.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);
            return this;
        }
    }
}
