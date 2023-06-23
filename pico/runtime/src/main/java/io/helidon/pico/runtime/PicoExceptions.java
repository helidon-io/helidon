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

package io.helidon.pico.runtime;

import java.util.Objects;

import io.helidon.pico.api.CallingContext;

import static io.helidon.pico.api.PicoServicesHolder.DEBUG_HINT;

/**
 * Utility for exceptions with information about debugging.
 */
public final class PicoExceptions {
    private PicoExceptions() {
    }

    /**
     * Convenience method for producing an error message that may involve advising the user to apply a debug mode.
     *
     * @param callingContext the calling context (caller can be using a custom calling context, which is why we accept it here
     *                       instead of using the global one)
     * @param msg            the base message to display
     * @return the message appropriate for any exception being thrown
     */
    public static String toErrorMessage(CallingContext callingContext, String msg) {
        Objects.requireNonNull(callingContext);
        Objects.requireNonNull(msg);

        return msg + " - previous calling context: " + callingContext;
    }

    /**
     * Convenience method for producing an error message that may involve advising the user to apply a debug mode. Use
     * {@link #toErrorMessage(io.helidon.pico.api.CallingContext, String)} instead f a calling context is available.
     *
     * @param msg the base message to display
     * @return the message appropriate for any exception being thrown
     * @see #toErrorMessage(io.helidon.pico.api.CallingContext, String)
     */
    public static String toErrorMessage(String msg) {
        Objects.requireNonNull(msg);

        return msg + " - " + DEBUG_HINT;
    }
}
