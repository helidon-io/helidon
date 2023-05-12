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

package io.helidon.pico.api;

import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * Internal bootstrap is what we store when {@link io.helidon.pico.api.PicoServices#globalBootstrap(Bootstrap)} is used.
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
     * Only populated when {@link io.helidon.pico.api.PicoServicesConfig#TAG_DEBUG} is set.
     *
     * @return the calling context
     */
    abstract Optional<CallingContext> callingContext();

    /**
     * Creates an internal bootstrap.
     * See the notes in {@link CallingContextFactory#create(boolean)}.
     *
     * @param bootstrap      Optionally, the user-defined bootstrap - one will be created if passed as null
     * @param callingContext Optionally, the calling context if known
     * @return a newly created internal bootstrap instance
     */
    static InternalBootstrap create(Bootstrap bootstrap,
                                    CallingContext callingContext) {
        if (callingContext == null) {
            callingContext = CallingContextFactory.create(false).orElse(null);
        }
        return InternalBootstrapDefault.builder()
                .bootStrap((bootstrap == null) ? BootstrapDefault.builder().build() : bootstrap)
                .callingContext(Optional.ofNullable(callingContext))
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
