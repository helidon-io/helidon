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

import java.util.regex.Pattern;

import io.helidon.config.Config;

class RegistryFilterSettingsImpl implements RegistryFilterSettings {

    static Builder builder() {
        return new Builder();
    }

    private final Pattern includePattern;
    private final Pattern excludePattern;

    private RegistryFilterSettingsImpl(Builder builder) {
        includePattern = builder.includeFilter == null || builder.includeFilter.isBlank()
                ? null
                : Pattern.compile(builder.includeFilter);
        excludePattern = builder.excludeFilter == null || builder.excludeFilter.isBlank()
                ? null
                : Pattern.compile(builder.excludeFilter);
    }

    @Override
    public boolean passes(String metricName) {
        return (excludePattern == null || !excludePattern.matcher(metricName).matches())
                && (includePattern == null || includePattern.matcher(metricName).matches());
    }

    static class Builder implements RegistryFilterSettings.Builder {

        private String includeFilter;
        private String excludeFilter;

        @Override
        public RegistryFilterSettings build() {
            return new RegistryFilterSettingsImpl(this);
        }

        @Override
        public RegistryFilterSettings.Builder exclude(String excludeFilter) {
            this.excludeFilter = excludeFilter;
            return this;
        }

        @Override
        public RegistryFilterSettings.Builder include(String includeFilter) {
            this.includeFilter = includeFilter;
            return this;
        }

        @Override
        public RegistryFilterSettings.Builder config(Config config) {
            config.get(EXCLUDE_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::exclude);
            config.get(INCLUDE_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::include);
            return this;
        }
    }
}
