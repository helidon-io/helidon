/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Services;

class MetricsConfigSupport {

    // Pattern of a single tag assignment (tag=value):
    //   - capture reluctant match of anything
    //   - non-capturing match of an unescaped =
    //   - capture the rest.
    static final Pattern TAG_ASSIGNMENT_PATTERN = Pattern.compile("(.*?)(?<!\\\\)=(.*)");

    private MetricsConfigSupport() {
    }

    /**
     * Looks up a single config value within the metrics configuration by config key.
     *
     * @param metricsConfig the {@link io.helidon.common.config.Config} node containing the metrics configuration
     * @param key           config key to fetch
     * @return config value
     */
    @Prototype.PrototypeMethod
    static Optional<String> lookupConfig(MetricsConfig metricsConfig, String key) {
        return metricsConfig.config()
                .get(key)
                .asString()
                .asOptional();
    }

    @Prototype.ConfigFactoryMethod("tags")
    static List<Tag> createTags(Config globalTagExpression) {
        return createTags(globalTagExpression.asString().get());
    }

    static List<Tag> createTags(String pairs) {
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
                                   // Do not use Tag.create in the next line. That would delegate to the MetricsFactoryManager
                                   // which, ultimately, might try to load config to set up the MetricFactory. But we are
                                   // already trying to load config and that would set up an infinite recursive loop.
                                   NoOpTag.create(name,
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
            throw new IllegalArgumentException("Error(s) in tag expression: " + allErrors);
        }
        return result.values()
                .stream()
                .toList();
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<MetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(MetricsConfig.BuilderBase<?, ?> builder) {
            if (builder.config().isEmpty()) {
                builder.config(Services.get(Config.class).get(MetricsConfigBlueprint.METRICS_CONFIG_KEY));
            }
            if (builder.keyPerformanceIndicatorMetricsConfig().isEmpty()) {
                builder.keyPerformanceIndicatorMetricsConfig(KeyPerformanceIndicatorMetricsConfig.create());
            }
            if (builder.scoping().isEmpty()) {
                builder.scoping(ScopingConfig.create());
            }
        }
    }

    static class RestRequestEnabledDecorator implements Prototype.OptionDecorator<MetricsConfig.BuilderBase<?, ?>, Optional<Boolean>> {

        @Override
        public void decorate(MetricsConfig.BuilderBase<?, ?> builder, Optional<Boolean> optionValue) {
            optionValue.ifPresent(builder::restRequestEnabled);
        }
    }
}
