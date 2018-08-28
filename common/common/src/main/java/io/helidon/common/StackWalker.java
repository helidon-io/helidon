/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A dummy replicate of Java9's StackWalker.
 */
public final class StackWalker {
    private static final StackWalker INSTANCE = new StackWalker();

    static {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("getStackWalker"));
        }
    }

    /**
     * Returns a {@code StackWalker} instance with default options.
     *
     * @return a {@code StackWalker} configured with the default options
     */
    public static StackWalker getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a {@code StackWalker} instance with the given {@code option} specifying
     * the stack frame information it can access.
     *
     * @param option ignored (used by java9 forward)
     * @return a {@code StackWalker} configured with the given options
     */
    public static StackWalker getInstance(Option option) {
        // ignore options
        return INSTANCE;
    }

    /**
     * Returns a {@code StackWalker} instance with the given {@code options} specifying
     * the stack frame information it can access.
     *
     * @param options ignored (used by java9 forward)
     * @return a {@code StackWalker} configured with the given options
     */
    public static StackWalker getInstance(Set<Option> options) {
        // ignore options
        return INSTANCE;
    }

    /**
     * Gets the {@code Class} object of the caller who invoked the method
     * that invoked {@code getCallerClass}.
     *
     * @return {@code Class} object of the caller's caller invoking this method.
     */
    public Class<?> getCallerClass() {
        Class<?>[] classContext = new MySecurityManager().getClassContext0();
        return classContext[3];
    }

    private StackWalker() {
    }

    /**
     * Applies the given function to the stream of {@code StackFrame}s
     * for the current thread, traversing from the top frame of the stack,
     * which is the method calling this {@code walk} method.
     *
     * @param function a function that takes a stream of
     *                 {@linkplain StackTraceElement stack frames} and returns a result.
     * @return the result of applying the function to the stream of
     * {@linkplain StackTraceElement stack frame}.
     */
    public Optional<StackTraceElement> walk(Function<? super Stream<StackTraceElement>, Optional<StackTraceElement>> function) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        return function.apply(Arrays.stream(stackTrace, 1, stackTrace.length));
    }

    /**
     * Option for future partial compatibility with Java9. Does nothing.
     */
    public enum Option {
        /**
         * In java9 used to tell the stack walker to keep class references, so we can
         * obtain the class name from the stack frames.
         * Does nothing here, as the {@link StackTraceElement} contains this information by default
         */
        RETAIN_CLASS_REFERENCE
    }

    private static class MySecurityManager extends SecurityManager {
        /**
         * Get class context (class stack).
         *
         * @return classes on the stack
         */
        public Class<?>[] getClassContext0() {
            return super.getClassContext();
        }
    }
}
