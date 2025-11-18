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

import java.util.Objects;
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;

class ScopeConfigSupport {

    private ScopeConfigSupport() {
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
