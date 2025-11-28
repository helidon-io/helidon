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

package io.helidon.builder.codegen;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Deprecated option information.
 */
@Prototype.Blueprint(detach = true)
interface OptionDeprecationBlueprint {
    /**
     * Deprecation message.
     *
     * @return deprecation message
     */
    String message();

    /**
     * If this is scheduled for removal, defaults to {@code true}.
     *
     * @return whether scheduled for removal
     */
    @Option.DefaultBoolean(true)
    boolean forRemoval();

    /**
     * Name of the option to use instead of this one.
     *
     * @return alternative option name
     */
    Optional<String> alternative();

    /**
     * Version that deprecated this option.
     *
     * @return since version
     */
    Optional<String> since();
}
