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

package io.helidon.inject.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Factory for creating {@link CallingContext} and builders for the calling context.
 * After a calling context builder is created, it should be amended with as much contextual information as possible, and then
 * optionally set globally using {@link #globalCallingContext(CallingContext, boolean)}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class CallingContextFactory {
    private static volatile CallingContext defaultCallingContext;

    private CallingContextFactory() {
    }

    /**
     * Sets the default global calling context.
     *
     * @param callingContext the default global context
     * @param throwIfAlreadySet should an exception be thrown if the global calling context was already set
     * @throws java.lang.IllegalStateException if context was already set and the throwIfAlreadySet is active
     */
    public static void globalCallingContext(CallingContext callingContext,
                                            boolean throwIfAlreadySet) {
        Objects.requireNonNull(callingContext);

        CallingContext global = defaultCallingContext;
        if (global != null && throwIfAlreadySet) {
            CallingContext currentCallingContext = CallingContextFactory.create(true).orElseThrow();
            throw new IllegalStateException("Expected to be the owner of the calling context. This context is: "
                                                    + currentCallingContext + "\n Context previously set was: " + global);
        }

        CallingContextFactory.defaultCallingContext = callingContext;
    }

    /**
     * Creates a new calling context instance. Normally this method will return a context optionally only when debug is
     * enabled. This behavior can be overridden by passing the {@code force=true} flag.
     *
     * @param force forces the creation of the calling context even when debug is disabled
     * @return a new calling context if there is an indication that debug mode is enabled, or if the force flag is set
     * @see InjectionServicesConfig#debug()
     */
    public static Optional<CallingContext> create(boolean force) {
        Optional<CallingContext.Builder> optBuilder = createBuilder(force);
        return optBuilder.map(CallingContext.Builder::build);

    }

    /**
     * Creates a new calling context builder instance. Normally this method will return a context builder optionally only when
     * debug is enabled. This behavior can be overridden by passing the {@code force=true} flag.
     *
     * @param force forces the creation of the calling context even when debug is disabled
     * @return a new calling context builder if there is an indication that debug mode is enabled, or if the force flag is set
     * @see InjectionServicesConfig#debug()
     */
    public static Optional<CallingContext.Builder> createBuilder(boolean force) {
        if (force || InjectionServices.injectionServices()
                .map(InjectionServices::config)
                .map(InjectionServicesConfig::shouldDebug)
                .orElse(false)) {

            return Optional.of(CallingContext.builder()
                                       .stackTrace(new RuntimeException().getStackTrace()));
        }
        return Optional.empty();
    }

}
