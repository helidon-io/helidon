/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
     * Sets the retained meter-name inclusion pattern from a string; core metrics ignores the setting.
     *
     * @param builderBase builder
     * @param includeString inclusion pattern
     * @deprecated Use {@link ScopeConfig.Builder#include(Pattern)}. Core metrics ignores scope configuration, and this
     * method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"27.0.0\")")
    @Prototype.BuilderMethod
    static void include(ScopeConfig.BuilderBase<?, ?> builderBase, String includeString) {
        Objects.requireNonNull(includeString, "include expression");
        builderBase.include(Pattern.compile(includeString));
    }

    /**
     * Sets the retained meter-name exclusion pattern from a string; core metrics ignores the setting.
     *
     * @param builderBase builder
     * @param excludeString exclusion pattern
     * @deprecated Use {@link ScopeConfig.Builder#exclude(Pattern)}. Core metrics ignores scope configuration, and this
     * method will be removed.
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @Prototype.Annotated("java.lang.Deprecated(forRemoval = true, since = \"27.0.0\")")
    @Prototype.BuilderMethod
    static void exclude(ScopeConfig.BuilderBase<?, ?> builderBase, String excludeString) {
        Objects.requireNonNull(excludeString, "exclude expression");
        builderBase.exclude(Pattern.compile(excludeString));
    }
}
