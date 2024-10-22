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

/**
 * Code generation extension for Helidon Service Registry.
 */
interface RegistryCodegenExtension {
    /**
     * Process a single round.
     *
     * @param roundContext round context
     */
    void process(RegistryRoundContext roundContext);

    /**
     * Called when the processing is over, and there will not be an additional processing round.
     */
    default void processingOver() {
    }
}
