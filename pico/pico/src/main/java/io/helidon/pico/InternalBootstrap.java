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

package io.helidon.pico;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.pico.spi.CallingContext;

/**
 * Internal bootstrap is what we store when {@link io.helidon.pico.PicoServices#globalBootstrap(Bootstrap)} is used.
 */
@Builder
abstract class InternalBootstrap {

    /**
     * The user established bootstrap.
     *
     * @return user establised bootstrap
     */
     abstract Bootstrap bootStrap();

    /**
     * Only populated when {@link io.helidon.pico.PicoServicesConfig#TAG_DEBUG} is set.
     *
     * @return the calling context
     */
    abstract Optional<CallingContext> callingContext();

    /**
     * Creates an internal bootstrap.
     * See the notes in {@link io.helidon.pico.spi.CallingContext#maybeCreate(java.util.Optional, java.util.Optional)}.
     *
     * @param bootstrap      Optionally, the user-defined bootstrap - one will be created if passed as null
     * @param callingContext Optionally, the calling context if know - defaults to {@link io.helidon.pico.spi.CallingContext}
     * @return a newly created internal bootstrap instance
     */
    static InternalBootstrap create(
            Bootstrap bootstrap,
            CallingContext callingContext) {
        return DefaultInternalBootstrap.builder()
                .bootStrap((bootstrap == null) ? DefaultBootstrap.builder().build() : bootstrap)
                .callingContext((callingContext == null) ? CallingContext.maybeCreate() : Optional.empty())
                .build();
    }

    /**
     * Creates a calling context when nothing is known from the caller's perspective.
     *
     * @return a newly created internal bootstrap instance
     */
    static InternalBootstrap create() {
        return create(null, null);
    }

}
