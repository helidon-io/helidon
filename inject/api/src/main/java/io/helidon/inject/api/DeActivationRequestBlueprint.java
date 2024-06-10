/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Request to deactivate a {@link ServiceProvider}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
interface DeActivationRequestBlueprint {
    /**
     * Whether to throw an exception on failure, or return it as part of the result.
     *
     * @return throw on failure
     */
    @ConfiguredOption("true")
    boolean throwIfError();
}
