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

package io.helidon.inject.configdriven.tests.config;

import java.util.Optional;

import io.helidon.builder.api.Prototype;

/**
 * aka Traceable.
 * Tracing configuration that can be enabled or disabled.
 */
@Prototype.Configured
@Prototype.Blueprint
interface FakeTraceableConfigBlueprint {
    /**
     * Whether this trace should be executed or not.
     *
     * @return {@code true} if span/component should be traced,
     *         {@code false} if it should not,
     *         {@code empty} when this flag is not explicitly configured
     */
    Optional<Boolean> isEnabled();

    /**
     * Name of this traceable unit.
     *
     * @return name
     */
    String name();

    /**
     * Whether this traceable should be executed or not.
     *
     * @return {@code true} if span/component should be traced,
     *         {@code false} if it should not
     */
    default boolean enabled() {
        return isEnabled().orElse(true);
    }

}
