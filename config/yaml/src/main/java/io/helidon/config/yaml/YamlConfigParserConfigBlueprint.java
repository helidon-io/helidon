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

package io.helidon.config.yaml;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Configuration of a YAML parser.
 */
@Api.Stable
@Prototype.Blueprint
@Prototype.Configured
interface YamlConfigParserConfigBlueprint extends Prototype.Factory<YamlConfigParser> {
    /**
     * Treat YAML mapping keys as literal names, including dots and tildes.
     * Applies to quoted and unquoted keys at every mapping level, including mappings within lists.
     * When disabled, mapping keys retain Helidon path syntax: dots separate segments, and {@code ~1} and {@code ~0}
     * represent literal dots and tildes. Defaults to {@code false} for compatibility.
     * <p>
     * Configuration lookup always uses Helidon path syntax. With literal keys enabled, the YAML key {@code a.b}
     * is accessed with {@code config.get("a~1b")}, and {@code a~1b} with {@code config.get("a~01b")}.
     *
     * @return whether mapping keys are literal names
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean literalKeys();
}
