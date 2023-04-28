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

/**
 * Factory for creating {@link CallingContext} and builders for the calling context.
 * After a calling context builder is created, it should be amended with as much contextual information as possible, and then
 * optionally set globally using {@link CallingContext#globalCallingContext(CallingContext, boolean)}.
 */
public class CallingContextFactory {

    private CallingContextFactory() {
    }

    /**
     * Creates a new calling context instance. Normally this method will return a context optionally only when debug is
     * enabled. This behavior can be overridden by passing the {@code force=true} flag.
     *
     * @param force forces the creation of the calling context even when debug is disabled
     * @return a new calling context if there is an indication that debug mode is enabled, or if the force flag is set
     * @see io.helidon.pico.api.PicoServices#isDebugEnabled()
     */
    public static Optional<CallingContext> create(boolean force) {
        Optional<CallingContextDefault.Builder> optBuilder = createBuilder(force);
        return optBuilder.map(CallingContextDefault.Builder::build);

    }

    /**
     * Creates a new calling context builder instance. Normally this method will return a context builder optionally only when
     * debug is enabled. This behavior can be overridden by passing the {@code force=true} flag.
     *
     * @param force forces the creation of the calling context even when debug is disabled
     * @return a new calling context builder if there is an indication that debug mode is enabled, or if the force flag is set
     * @see io.helidon.pico.api.PicoServices#isDebugEnabled()
     */
    public static Optional<CallingContextDefault.Builder> createBuilder(boolean force) {
        boolean createIt = (force || PicoServices.isDebugEnabled());
        if (!createIt) {
            return Optional.empty();
        }

        return Optional.of(CallingContextDefault.builder()
                                   .trace(new RuntimeException().getStackTrace()));
    }

}
