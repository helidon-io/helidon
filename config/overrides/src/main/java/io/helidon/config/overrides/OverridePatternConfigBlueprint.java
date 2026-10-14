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

package io.helidon.config.overrides;

import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * A regular expression and replacement value used by an override filter provider.
 */
@Prototype.Blueprint
@Prototype.Configured
interface OverridePatternConfigBlueprint {
    /**
     * Regular expression matched against the entire configuration key. Pattern flags supplied programmatically
     * are retained; configuration strings can use embedded flags such as {@code (?i)}.
     *
     * @return required matching pattern
     */
    @Option.Required
    @Option.Configured
    Pattern pattern();

    /**
     * Replacement for an existing matching value. References are resolved against the target configuration.
     *
     * @return required replacement value
     */
    @Option.Required
    @Option.Configured
    String value();
}
