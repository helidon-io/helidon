/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.spi;

/**
 * Handles shutdown of the VM.
 * <p>
 * Register an instance with {@link io.helidon.Main#addShutdownHandler(HelidonShutdownHandler)},
 * remove with {@link io.helidon.Main#removeShutdownHandler(HelidonShutdownHandler)}.
 * Shutdown handlers are compared via instances, not via equals methods.
 */
@FunctionalInterface
public interface HelidonShutdownHandler {
    /**
     * Handle the shutdown work of this component.
     */
    void shutdown();
}
