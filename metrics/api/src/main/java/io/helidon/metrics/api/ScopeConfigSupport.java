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

import java.util.Objects;
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;

class ScopeConfigSupport {

    private ScopeConfigSupport() {
    }

    /**
     * Indicates whether the specified meter is enabled according to the scope configuration.
     *
     * @param scopeConfig scope configuration
     * @param name        meter name to check
     * @return whether the meter is enabled
     */
    @Prototype.PrototypeMethod
    static boolean isMeterEnabled(ScopeConfig scopeConfig, String name) {
        /*
         The following must be true for the meter to be enabled:

         1. The scope itself must be enabled (that's the default).
         2. If there is an exclude pattern, the name must not match it.
         3. If there is an include pattern, the name must match it.
         */
        return scopeConfig.enabled()
                && scopeConfig.exclude().map(excludePattern -> !excludePattern.matcher(name).matches()).orElse(true)
                && scopeConfig.include().map(includePattern -> includePattern.matcher(name).matches()).orElse(true);

    }

    /**
     * Sets the include expression using a {@link java.lang.String} compiled automatically
     * into a {@link java.util.regex.Pattern}.
     *
     * @param builderBase builder
     * @param includeString include string
     */
    @Prototype.BuilderMethod
    static void include(ScopeConfig.BuilderBase<?, ?> builderBase, String includeString) {
        Objects.requireNonNull(includeString, "include expression");
        builderBase.include(Pattern.compile(includeString));
    }

    /**
     * Sets the exclude expression using a {@link java.lang.String} compiled automatically
     * into a {@link java.util.regex.Pattern}.
     *
     * @param builderBase builder
     * @param excludeString exclude string
     */
    @Prototype.BuilderMethod
    static void exclude(ScopeConfig.BuilderBase<?, ?> builderBase, String excludeString) {
        Objects.requireNonNull(excludeString, "exclude expression");
        builderBase.exclude(Pattern.compile(excludeString));
    }
}
