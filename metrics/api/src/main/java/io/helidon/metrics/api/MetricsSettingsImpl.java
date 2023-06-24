/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

class MetricsSettingsImpl implements MetricsSettings {

    private static final RegistrySettings DEFAULT_REGISTRY_SETTINGS = RegistrySettings.create();

    private static final Set<String> PREDEFINED_SCOPES = Set.of(Registry.APPLICATION_SCOPE,
                                                                Registry.BASE_SCOPE,
                                                                Registry.VENDOR_SCOPE);

    private final boolean isEnabled;
    private final KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings;
    private final BaseMetricsSettings baseMetricsSettings;
    private final Map<String, RegistrySettings> registrySettings;
    private final Map<String, String> globalTags;
    private final String appTagValue;
    private final Config metricsSettingsConfig;

    private MetricsSettingsImpl(MetricsSettingsImpl.Builder builder) {
        metricsSettingsConfig = builder.metricsSettingsConfig;
        isEnabled = builder.isEnabled;
        kpiMetricsSettings = builder.kpiMetricsSettingsBuilder.build();
        baseMetricsSettings = builder.baseMetricsSettingsBuilder.build();
        registrySettings = builder.registrySettings;
        globalTags = builder.globalTags;
        appTagValue = builder.appTagValue;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorSettings() {
        return kpiMetricsSettings;
    }

    @Override
    public BaseMetricsSettings baseMetricsSettings() {
        return baseMetricsSettings;
    }

    @Override
    public boolean isMetricEnabled(String scope, String metricName) {
        if (!isEnabled) {
            return false;
        }
        RegistrySettings registrySettings = this.registrySettings.get(scope);
        return registrySettings == null || registrySettings.isMetricEnabled(metricName);
    }

    @Override
    public RegistrySettings registrySettings(String scope) {
        return registrySettings.getOrDefault(scope, DEFAULT_REGISTRY_SETTINGS);
    }

    @Override
    public Map<String, String> globalTags() {
        return globalTags;
    }

    @Override
    public String appTagValue() {
        return appTagValue;
    }

    @Override
    public String value(String key) {
        return metricsSettingsConfig != null ? metricsSettingsConfig.get(key).asString().orElse(null) : null;
    }

    // For testing and within-package use only
    Map<String, RegistrySettings> registrySettings() {
        return registrySettings;
    }

    static class Builder implements MetricsSettings.Builder {

        private boolean isEnabled = true;
        private KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettingsBuilder =
                KeyPerformanceIndicatorMetricsSettings.builder();
        private BaseMetricsSettings.Builder baseMetricsSettingsBuilder = BaseMetricsSettings.builder();
        private final Map<String, RegistrySettings> registrySettings = prepareRegistrySettings();
        private Map<String, String> globalTags = Collections.emptyMap();
        private String appTagValue;
        private Config metricsSettingsConfig;

        private static Map<String, RegistrySettings> prepareRegistrySettings() {
            Map<String, RegistrySettings> result = new HashMap<>();
            for (String predefinedScope : PREDEFINED_SCOPES) {
                result.put(predefinedScope, RegistrySettings.create());
            }
            return result;
        }

        protected Builder() {
        }

        protected Builder(MetricsSettings serviceSettings) {
            isEnabled = serviceSettings.isEnabled();
            kpiMetricsSettingsBuilder = KeyPerformanceIndicatorMetricsSettings.builder(
                    serviceSettings.keyPerformanceIndicatorSettings());
            baseMetricsSettingsBuilder = BaseMetricsSettings.builder(serviceSettings.baseMetricsSettings());

            registrySettings.putAll(((MetricsSettingsImpl) serviceSettings).registrySettings());
        }

        @Override
        public MetricsSettingsImpl build() {
            return new MetricsSettingsImpl(this);
        }

        @Override
        public Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public Builder baseMetricsSettings(BaseMetricsSettings.Builder baseMetricsSettingsBuilder) {
            this.baseMetricsSettingsBuilder = baseMetricsSettingsBuilder;
            return this;
        }

        @Override
        public Builder config(Config metricsSettingsConfig) {
            this.metricsSettingsConfig = metricsSettingsConfig;
            baseMetricsSettingsBuilder.config(metricsSettingsConfig.get(BaseMetricsSettings.Builder.BASE_METRICS_CONFIG_KEY));
            kpiMetricsSettingsBuilder.config(metricsSettingsConfig
                                                     .get(KeyPerformanceIndicatorMetricsSettings.Builder
                                                                  .KEY_PERFORMANCE_INDICATORS_CONFIG_KEY));
            metricsSettingsConfig.get(MetricsSettings.Builder.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);

            metricsSettingsConfig.get(REGISTRIES_CONFIG_KEY)
                    .asList(ScopedRegistrySettingsImpl::create)
                    .ifPresent(this::addAllTypedRegistrySettings);

            metricsSettingsConfig.get(GLOBAL_TAGS_CONFIG_KEY)
                    .as(Builder::globalTagsExpressionToMap)
                    .ifPresent(this::globalTags);

            metricsSettingsConfig.get(APP_TAG_CONFIG_KEY)
                    .asString()
                    .ifPresent(this::appTagValue);
            return this;
        }

        @Override
        public Builder keyPerformanceIndicatorSettings(
                KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettings) {
            this.kpiMetricsSettingsBuilder = kpiMetricsSettings;
            return this;
        }

        @Override
        public MetricsSettings.Builder registrySettings(String scope, RegistrySettings registrySettings) {
            this.registrySettings.put(scope, registrySettings);
            return this;
        }

        @Override
        public MetricsSettings.Builder globalTags(Map<String, String> globalTags) {
            this.globalTags = new HashMap<>(globalTags);
            return this;
        }

        @Override
        public MetricsSettings.Builder appTagValue(String appTagValue) {
            this.appTagValue = appTagValue;
            return this;
        }

        private void addAllTypedRegistrySettings(List<ScopedRegistrySettingsImpl> typedRegistrySettingsList) {
            typedRegistrySettingsList.forEach(settings -> registrySettings.put(settings.scope, settings));
        }

        private static Map<String, String> globalTagsExpressionToMap(Config globalTagExpression) {
            return globalTagsExpressionToMap(globalTagExpression.asString().get());
        }

        static Map<String, String> globalTagsExpressionToMap(String pairs) {

            Map<String, String> result = new HashMap<>();
            List<String> errorPairs = new ArrayList<>();
            String[] assignments = pairs.split("(?<!\\\\),"); // split using non-escaped commas
            int position = 0;
            for (String assignment : assignments) {
                List<String> errors = new ArrayList<>();
                if (assignment.isBlank()) {
                    errors.add("empty assignment at position " + position + ": " + assignment);
                } else {
                    String[] parts = assignment.split("(?<!\\\\)="); // split using non-escaped equals sign
                    if (parts.length != 2) {
                        errors.add("expected 2 parts separated by =; found '" + assignment + "' containing " + parts.length);
                    } else {
                        String name = parts[0];
                        String value = parts[1];
                        if (name.isBlank()) {
                            errors.add("missing tag name");
                        }
                        if (value.isBlank()) {
                            errors.add("missing tag value");
                        }
                        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                            errors.add("tag name must start with a letter and include only letters, digits, and underscores");
                        }
                        if (errors.isEmpty()) {
                            result.put(name,
                                       value.replace("\\,", ",")
                                               .replace("\\=", "="));
                        }
                    }
                }
                if (!errors.isEmpty()) {
                    errorPairs.add("Position " + position + " with expression " + assignment + ": " + errors);
                }
                position++;
            }
            if (!errorPairs.isEmpty()) {
                throw new IllegalArgumentException("Error(s) in global tag expression: " + errorPairs);
            }
            return result;
        }

        private static class ScopedRegistrySettingsImpl extends RegistrySettingsImpl {

            static ScopedRegistrySettingsImpl create(Config registrySettingsConfig) {

                RegistrySettingsImpl.Builder builder = RegistrySettingsImpl.builder();
                builder.config(registrySettingsConfig);

                ConfigValue<String> scopeValue = registrySettingsConfig.get(RegistrySettings.Builder.SCOPE_CONFIG_KEY)
                        .asString();
                if (!scopeValue.isPresent()) {
                    throw new IllegalArgumentException("Missing metric registry scope in registry settings: "
                                                               + registrySettingsConfig);
                }
                return new ScopedRegistrySettingsImpl(scopeValue.get(), builder);
            }

            private final String scope;

            private ScopedRegistrySettingsImpl(String scope, RegistrySettingsImpl.Builder builder) {
                super(builder);
                this.scope = scope;
            }
        }
    }
}
