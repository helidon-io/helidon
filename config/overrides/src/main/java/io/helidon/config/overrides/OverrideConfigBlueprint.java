/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.config.spi.ConfigSource;

/**
 * Configuration of the override filter.
 */
@Prototype.Blueprint(decorator = OverrideConfigSupport.Decorator.class)
interface OverrideConfigBlueprint extends Prototype.Factory<OverrideConfigFilter> {
    /**
     * Explicit config override settings.
     *
     * @return a map of a pattern (regular expression) to a value, i.e. {@code prod\.\w+\.logging\.level=ERROR}
     */
    @Option.Singular
    Map<Pattern, String> overridePatterns();

    /**
     * Explicit config override settings, using an expression with {@code *} to replace words.
     * This method is not using regular expressions.
     *
     * @return a map of an expression to a value, i.e. {@code prod.*.logging.level=ERROR}
     */
    @Option.Singular
    Map<String, String> overrideExpressions();

    /**
     * Config sources to use to lookup overrides. The config source content is expected to be a map of expressions
     * (using * as a replacement for any word) to values.
     *
     * @return config sources to use to obtain the configuration of overrides
     */
    @Option.Singular
    List<ConfigSource> configSources();

    /**
     * Whether to check the configuration we are overriding for configuration of override filter.
     * <p>
     * The key we check is {@value OverrideConfigFilter#CONFIG_KEY}.
     *
     * @return if set to true, we will honor override configuration from target config; if both config sources are set,
     * and the target config contains configuration overrides we will merge the configuration
     */
    @Option.DefaultBoolean(true)
    boolean useTargetConfig();
}
