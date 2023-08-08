/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.inject.configdriven.api.ConfigBean;

/**
 * Config bean for {@link io.helidon.metrics.api.MetricsConfig}.
 */
@ConfigBean()
@Configured(root = true, prefix = MetricsConfigBlueprint.METRICS_CONFIG_KEY)
@Prototype.Blueprint(decorator = MetricsConfigBlueprint.BuilderDecorator.class)
interface MetricsConfigBlueprint {

    /**
     * The config key containing settings for all of metrics.
     */
    String METRICS_CONFIG_KEY = "metrics";

    /**
     * Config key for comma-separated, {@code tag=value} global tag settings.
     */
    String GLOBAL_TAGS_CONFIG_KEY = "tags";

    /**
     * Config key for the app tag value to be applied to all metrics in this application.
     */
    String APP_TAG_CONFIG_KEY = "appName";



    /**
     * Whether metrics functionality is enabled.
     *
     * @return if metrics are configured to be enabled
     */
    @ConfiguredOption("true")
    boolean enabled();

    /**
     * Key performance indicator metrics settings.
     *
     * @return key performance indicator metrics settings
     */
    @ConfiguredOption(key = KeyPerformanceIndicatorMetricsConfigBlueprint.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
    Optional<KeyPerformanceIndicatorMetricsConfig> keyPerformanceIndicatorMetricsConfig();

    /**
     * Global tags.
     *
     * @return name/value pairs for global tags
     */
    @ConfiguredOption(key = GLOBAL_TAGS_CONFIG_KEY)
    List<Tag> globalTags();

    @ConfiguredOption(key = APP_TAG_CONFIG_KEY)
    Optional<String> appTagValue();

    class BuilderDecorator implements Prototype.BuilderDecorator<MetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(MetricsConfig.BuilderBase<?, ?> target) {
            if (target.config().isEmpty()) {
                target.config(GlobalConfig.config().get(METRICS_CONFIG_KEY));
            }
        }
    }
}
