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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.inject.configdriven.api.ConfigBean;

/**
 * Blueprint for {@link io.helidon.metrics.api.MetricsConfig}.
 */
@ConfigBean()
@Configured(root = true, prefix = MetricsConfigBlueprint.METRICS_CONFIG_KEY)
@Prototype.Blueprint(decorator = MetricsConfigBlueprint.BuilderDecorator.class)
@Prototype.CustomMethods(MetricsConfigSupport.class)
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
    String APP_TAG_CONFIG_KEY = "app-name";

    // TODO - rely on programmatic metrics config so SE can set one default, MP another
    /**
     * Default name for scope tags.
     */
    String DEFAULT_SCOPE_TAG_NAME = "m-scope";

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

    /**
     * Application tag value added to each meter ID.
     *
     * @return  application tag value
     */
    @ConfiguredOption(key = APP_TAG_CONFIG_KEY)
    Optional<String> appTagValue();

    /**
     * Metrics configuration node.
     *
     * @return metrics configuration
     */
    Config config();

    /**
     * Tag name used for recording the scope of meters (defaults according to the active runtime).
     *
     * @return tag name for scope
     */
    String scopeTagName();

    class BuilderDecorator implements Prototype.BuilderDecorator<MetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(MetricsConfig.BuilderBase<?, ?> builder) {
            if (builder.config().isEmpty()) {
                builder.config(GlobalConfig.config().get(METRICS_CONFIG_KEY));
            }
            if (builder.scopeTagName().isEmpty()) {
                builder.scopeTagName(DEFAULT_SCOPE_TAG_NAME);
            }
        }
    }

    @Prototype.FactoryMethod
    static List<Tag> createGlobalTags(Config globalTagExpression) {
        return createGlobalTags(globalTagExpression.asString().get());
    }

    static List<Tag> createGlobalTags(String pairs) {
        // Use a TreeMap to order by tag name.
        Map<String, Tag> result = new TreeMap<>();
        List<String> allErrors = new ArrayList<>();
        String[] assignments = pairs.split("(?<!\\\\),"); // split using non-escaped equals sign
        int position = 0;
        for (String assignment : assignments) {
            List<String> errorsForThisAssignment = new ArrayList<>();
            if (assignment.isBlank()) {
                errorsForThisAssignment.add("empty assignment at position " + position + ": " + assignment);
            } else {
                // Pattern should yield group 1 = tag name and group 2 = tag value.
                Matcher matcher = MetricsConfigSupport.TAG_ASSIGNMENT_PATTERN.matcher(assignment);
                if (!matcher.matches()) {
                    errorsForThisAssignment.add("expected tag=value but found '" + assignment + "'");
                } else {
                    String name = matcher.group(1);
                    String value = matcher.group(2);
                    if (name.isBlank()) {
                        errorsForThisAssignment.add("missing tag name");
                    }
                    if (value.isBlank()) {
                        errorsForThisAssignment.add("missing tag value");
                    }
                    if (!name.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                        errorsForThisAssignment.add(
                                "tag name must start with a letter and include only letters, digits, and underscores");
                    }
                    if (errorsForThisAssignment.isEmpty()) {
                        result.put(name,
                                   Tag.create(name,
                                              value.replace("\\,", ",")
                                                   .replace("\\=", "=")));
                    }
                }
            }
            if (!errorsForThisAssignment.isEmpty()) {
                allErrors.add(String.format("Position %d with expression %s: %s",
                                            position,
                                            assignment,
                                            errorsForThisAssignment));
            }
            position++;
        }
        if (!allErrors.isEmpty()) {
            throw new IllegalArgumentException("Error(s) in global tag expression: " + allErrors);
        }
        return result.values()
                .stream()
                .toList();
    }
}
