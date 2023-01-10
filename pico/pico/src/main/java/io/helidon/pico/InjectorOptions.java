/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Provides optional, contextual tunings to the {@link Injector}.
 *
 * @see Injector
 */
@Builder
public interface InjectorOptions {

    /**
     * The target finishing phase for the {@link Activator} behind the {@link Injector}.
     * The default is {@link Phase#ACTIVE}.
     *
     * @return the target finish phase
     */
    @ConfiguredOption("ACTIVE")
    Phase targetPhase();

    /**
     * The strategy the injector should apply. The default is {@link Injector.Strategy#ANY}.
     *
     * @return the injector strategy to use
     */
    @ConfiguredOption("ANY")
    Injector.Strategy strategy();

    /**
     * Whether to throw an exception on failure to activate, or return an error activation result on activation.
     *
     * @return whether to throw on failure
     */
    @ConfiguredOption("true")
    boolean throwOnFailure();

}
