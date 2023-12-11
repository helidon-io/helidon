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

package io.helidon.webserver.observe;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Base configuration for any observer that exposes an endpoint.
 */
@Prototype.Blueprint(builderPublic = false, createEmptyPublic = false, createFromConfigPublic = false)
@Prototype.Configured
interface ObserverConfigBaseBlueprint {
    /**
     * Whether this observer is enabled.
     *
     * @return {@code false} to disable observer
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean enabled();

    /**
     * Name of this observer. Each observer should provide its own default for this property.
     *
     * @return observer name
     */
    @Option.Required
    String name();
}
