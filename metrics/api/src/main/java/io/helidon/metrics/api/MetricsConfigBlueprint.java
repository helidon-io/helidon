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
 * Configuration settings for metrics.
 *
 *     <h2>Scope handling configuration</h2>
 *     Helidon allows developers to associate a scope with each meter. The {@value SCOPE_CONFIG_KEY} subsection of the
 *     {@value METRICS_CONFIG_KEY} configuration controls
 *     <ul>
 *         <li>the default scope value to use if a meter is registered without an explicit scope setting, and</li>
 *         <li>whether and how Helidon records each meter's scope as a tag in the underlying implementation meter registry.
 *         <p>
 *             Specifically, users can specify whether scope tags are used at all and, if so, what tag name to use.
 *         </li>
 *     </ul>
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

    /**
     * Config key for scope-related settings.
     */
    String SCOPE_CONFIG_KEY = "scope";

    /**
     * Config key for the default scope value.
     */
    String SCOPE_DEFAULT_VALUE_CONFIG_KEY = "default";

    /**
     * Config key for tag settings within the scope config section.
     */
    String SCOPE_TAG_CONFIG_KEY = "tag";

    /**
     * Config key for the tag name to use for storing scope values within the scope config section.
     */
    String SCOPE_TAG_NAME_CONFIG_KEY = "name";

    /**
     * Default tag name to use for recording a meter's scope as a tag (if that behavior is enabled).
     */
    String SCOPE_TAG_NAME_DEFAULT = "scope";

    /**
     * Config key for whether to store scopes as tags.
     */
    String SCOPE_TAG_ENABLED_CONFIG_KEY = "enabled";

    /**
     * Default setting for whether to use scope tags (as text).
     */
    String SCOPE_TAG_ENABLED_DEFAULT_VALUE = "false";

    /**
     * Default setting for whether to use scope tags (as boolean).
     */
    boolean SCOPE_TAG_ENABLED_DEFAULT = Boolean.parseBoolean(SCOPE_TAG_ENABLED_DEFAULT_VALUE);


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
     * @return application tag value
     */
    @ConfiguredOption(key = APP_TAG_CONFIG_KEY)
    Optional<String> appTagValue();

    /**
     * Default scope value to associate with meters that are registered without an explicit setting; no setting means meters
     * receive no default scope value.
     *
     * @return default scope value
     */
    @ConfiguredOption(key = SCOPE_CONFIG_KEY + "." + SCOPE_DEFAULT_VALUE_CONFIG_KEY)
    Optional<String> scopeDefaultValue();

    /**
     * Whether a meter's scope is recorded as a tag value in the meter's ID in the underlying implementation meter registry.
     *
     * @return if scopes are recorded as tags in the underlying implementation meter registry
     */
    @ConfiguredOption(key = SCOPE_CONFIG_KEY + "." + SCOPE_TAG_CONFIG_KEY + "." + SCOPE_TAG_ENABLED_CONFIG_KEY,
                      value = SCOPE_TAG_ENABLED_DEFAULT_VALUE)
    Boolean scopeTagEnabled();

    /**
     * Tag name for storing meter scope values in the underlying implementation meter registry.
     *
     * @return tag name for storing scope values
     */
    @ConfiguredOption(key = SCOPE_CONFIG_KEY + "." + SCOPE_TAG_CONFIG_KEY + "." + SCOPE_TAG_NAME_CONFIG_KEY,
                      value = SCOPE_TAG_NAME_DEFAULT)
    String scopeTagName();

    /**
     * Settings for individual scopes.
     *
     * @return scope settings
     */
    @ConfiguredOption
    Map<String, ScopeConfig> scopes();

    /**
     * Metrics configuration node.
     *
     * @return metrics configuration
     */
    Config config();

    /**
     * Determines whether the meter with the specified name and within the indicated scope is enabled.
     *
     * @param name meter name
     * @param scope scope name
     * @return whether the meter is enabled
     */
    boolean isMeterEnabled(String name, String scope);

    class BuilderDecorator implements Prototype.BuilderDecorator<MetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(MetricsConfig.BuilderBase<?, ?> builder) {
            if (builder.config().isEmpty()) {
                builder.config(GlobalConfig.config().get(METRICS_CONFIG_KEY));
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
            throw new IllegalArgumentException("Error(s) in global tag expression: " + allErrors);
        }
        return result.values()
                .stream()
                .toList();
    }
}
