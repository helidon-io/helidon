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

package io.helidon.pico.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.pico.PicoServices;

import static io.helidon.pico.PicoServicesConfig.TAG_DEBUG;

/**
 * For internal use only to Helidon. Applicable when {@link io.helidon.pico.PicoServicesConfig#TAG_DEBUG} is enabled.
 *
 * @deprecated for internal use only to helidon.
 */
@Builder(interceptor = CallingContext.BuilderInterceptor.class)
@Deprecated
public abstract class CallingContext {

    /**
     * Helpful hint to give developers needing to see more debug info.
     */
    public static final String DEBUG_HINT = "use the (-D and/or -A) tag '" + TAG_DEBUG + "=true' to see full trace output.";

    private static CallingContext defaultCallingContext;

    /**
     * This needs to be private since a generated builder will be extending this.
     */
    protected CallingContext() {
    }

    @Override
    public String toString() {
        return "module name: " + moduleName() + "; thread name: " + threadName() + "; trace:\n" + stackTraceOf(trace());
    }

    /**
     * Only populated when {@link io.helidon.pico.PicoServicesConfig#TAG_DEBUG} is set.
     *
     * @return the stack trace for who initialized
     */
    public abstract StackTraceElement[] trace();

    /**
     * Only populated when {@link io.helidon.pico.PicoServicesConfig#TAG_MODULE_NAME} is set.
     *
     * @return the module name
     */
    public abstract Optional<String> moduleName();

    /**
     * The thread that created the calling context.
     *
     * @return thread creating the calling context
     */
    public abstract String threadName();

    /**
     * Returns a stack trace as a list of strings.
     *
     * @param trace the trace
     * @return the list of strings for the stack trace
     */
    public static List<String> stackTraceOf(
            StackTraceElement[] trace) {
        List<String> result = new ArrayList<>();
        for (StackTraceElement e : trace) {
            result.add(e.toString() + "\n");
        }
        return result;
    }

    /**
     * Creates a new calling context instance.
     *
     * @param moduleName     the module name if known
     * @param isDebugEnabled the debug flag if known
     * @return a new calling context if there is indication that debug mode is enabled
     * @see io.helidon.pico.PicoServices#isDebugEnabled()
     */
    public static Optional<CallingContext> maybeCreate(
            Optional<String> moduleName,
            Optional<Boolean> isDebugEnabled) {
        boolean debug = isDebugEnabled.orElseGet(() -> PicoServices.isDebugEnabled());
        if (!debug) {
            return Optional.empty();
        }

        return Optional.of(DefaultCallingContext.builder()
                .moduleName(moduleName)
                .trace(new RuntimeException().getStackTrace())
                .build());
    }

    /**
     * Same as {@link #maybeCreate(boolean, java.util.Optional, java.util.Optional, boolean, boolean)} passing default args.
     *
     * @return a new calling context if there is indication that debug mode is enabled
     */
    public static Optional<CallingContext> maybeCreate() {
        return maybeCreate(false, Optional.empty(), Optional.empty(), false, false);
    }

    /**
     * Used when no contextual information is known from the caller's perspective.
     *
     * @param force force the creation of the calling context regardless of whether we are in debug mode
     * @param moduleName     the module name if known
     * @param isDebugEnabled the debug flag if known
     * @param setGlobalDefault should any created context be set in the global default
     * @param throwIfAlreadySet should an exception be thrown if the global calling context was already set
     * @return a new calling context if there is indication that debug mode is enabled
     */
    public static Optional<CallingContext> maybeCreate(
            boolean force,
            Optional<String> moduleName,
            Optional<Boolean> isDebugEnabled,
            boolean setGlobalDefault,
            boolean throwIfAlreadySet) {
        CallingContext global = defaultCallingContext;
        if (global != null) {
            return Optional.of(global);
        }

        if (force || isDebugEnabled.orElse(false)) {
            global = DefaultCallingContext.builder()
                    .moduleName(moduleName)
                    .trace(new RuntimeException().getStackTrace())
                    .build();
        } else {
            global = maybeCreate(Optional.empty(), Optional.empty()).orElse(null);
        }

        if (global != null && setGlobalDefault) {
            globalCallingContext(global, throwIfAlreadySet);
        }

        return Optional.ofNullable(global);
    }

    /**
     * Sets the default global context.
     *
     * @param callingContext the default global context
     * @param throwIfAlreadySet should an exception be thrown if the global calling context was already set
     * @throws java.lang.IllegalStateException if context was already set and the throwIfAlreadySet is active
     */
    public static void globalCallingContext(
            CallingContext callingContext,
            boolean throwIfAlreadySet) {
        Objects.requireNonNull(callingContext);

        CallingContext global = defaultCallingContext;
        if (global != null && throwIfAlreadySet) {
            CallingContext currentCallingContext =
                    maybeCreate(true, Optional.empty(), Optional.empty(), false, false).orElseThrow();
            throw new IllegalStateException("Expected to be the owner of the calling context. This context is: "
                                                    + currentCallingContext + "\n Context previously set was: " + global);
        }

        CallingContext.defaultCallingContext = callingContext;
    }

    /**
     * Convenience method for producing an error message that may involve advising the user to apply a debug mode.
     *
     * @param callingContext the calling context (caller can be using a custom calling context, which is why we accept it here instead of using the global one)
     * @param msg the base message to display
     * @return the message appropriate for any exception being thrown
     */
    public static String toErrorMessage(
            Optional<CallingContext> callingContext,
            String msg) {
        if (callingContext.isEmpty()) {
            return msg + " - " + DEBUG_HINT;
        } else {
            return msg + " - previous calling context: " + callingContext.orElseThrow();
        }
    }

    static class BuilderInterceptor implements io.helidon.builder.BuilderInterceptor<DefaultCallingContext.Builder> {
        @Override
        public DefaultCallingContext.Builder intercept(DefaultCallingContext.Builder target) {
            if (target.threadName() == null) {
                target.threadName(Thread.currentThread().getName());
            }
            return target;
        }
    }

}
