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

import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * Provides optional, contextual tunings to the {@link Injector}.
 *
 * @see Injector
 */
@Builder
public interface InjectorOptions {

    /**
     * The optional starting phase for the {@link Activator} behind the {@link Injector}.
     * The default is the current phase that the managed {@link ServiceProvider} is currently in.
     *
     * @return the optional target finish phase
     */
    Optional<Phase> startAtPhase();

    /**
     * The optional target finishing phase for the {@link Activator} behind the {@link Injector}.
     * The default is {@link Phase#ACTIVE}.
     *
     * @return the optional target finish phase
     */
    Optional<Phase> finishAtPhase();

    /**
     * The optional recipient target, describing who and what is being injected.
     *
     * @return the optional target injection point info
     */
    Optional<InjectionPointInfo> ipInfo();

    /**
     * The optional services registry to use, defaulting to {@link PicoServices#services()}.
     *
     * @return the optional services registry to use
     */
    Optional<Services> services();

    /**
     * The optional activation log that the injection should record its activity on.
     *
     * @return the optional activation log to use
     */
    Optional<ActivationLog> log();

    /**
     * The optional injection strategy the injector should apply. The default is {@link Injector.Strategy#ANY}.
     *
     * @return the optional injector strategy to use
     */
    Optional<Injector.Strategy> strategy();

}
