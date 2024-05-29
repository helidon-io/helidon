/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import io.helidon.codegen.Option;

/**
 * Supported options specific to Helidon Service Registry.
 */
final class ServiceOptions {
    /**
     * Treat all super types as a contract for a given service type being added.
     */
    public static final Option<Boolean> AUTO_ADD_NON_CONTRACT_INTERFACES =
            Option.create("helidon.registry.autoAddNonContractInterfaces",
                          "Treat all super types as a contract for a given service type being added.",
                          false);

    private ServiceOptions() {
    }
}
