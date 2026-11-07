/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.task;

import java.util.Set;

final class InterruptableTaskSupport {
    private static final RecursiveExecutionError RECURSIVE_EXECUTION = new RecursiveExecutionError();
    private static final ThreadLocal<InterruptableTask<?>> INVOCATION = new ThreadLocal<>();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                                                                                StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final ClassValue<RunImplementation> RUN_IMPLEMENTED = new ClassValue<>() {
        @Override
        protected RunImplementation computeValue(Class<?> type) {
            try {
                return type.getMethod("run").getDeclaringClass() == InterruptableTask.class
                        ? RunImplementation.DEFAULT
                        : RunImplementation.OVERRIDDEN;
            } catch (NoClassDefFoundError _) {
                // Reflection also resolves unrelated method signatures, which may use absent optional dependencies.
                return RunImplementation.UNRESOLVED;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("InterruptableTask must expose run()", e);
            }
        }
    };

    private InterruptableTaskSupport() {
    }

    static void run(InterruptableTask<?> task) {
        var implementation = RUN_IMPLEMENTED.get(task.getClass());
        if (implementation == RunImplementation.OVERRIDDEN) {
            task.run();
        } else if (implementation == RunImplementation.DEFAULT) {
            throw new UnsupportedOperationException("InterruptableTask must override run() or call()");
        } else {
            runWithoutLookup(task);
        }
    }

    private static void runWithoutLookup(InterruptableTask<?> task) {
        var previous = INVOCATION.get();
        if (previous == task && defaultRunInvoked()) {
            // Bypass the default run()'s Exception wrapper; the outer adapter translates this signal.
            throw RECURSIVE_EXECUTION;
        }
        INVOCATION.set(task);
        try {
            task.run();
        } catch (RecursiveExecutionError _) {
            throw new UnsupportedOperationException("InterruptableTask must override run() or call()");
        } finally {
            if (previous == null) {
                INVOCATION.remove();
            } else {
                INVOCATION.set(previous);
            }
        }
    }

    private static boolean defaultRunInvoked() {
        return STACK_WALKER.walk(frames -> {
            var iterator = frames.iterator();
            StackWalker.StackFrame callee = null;
            boolean currentInvocationSeen = false;
            while (iterator.hasNext()) {
                var frame = iterator.next();
                if (frame.getDeclaringClass() == InterruptableTaskSupport.class
                        && frame.getMethodName().equals("runWithoutLookup")) {
                    if (currentInvocationSeen) {
                        // An override can delegate to the default run(); inspect only the fallback's direct callee.
                        return callee != null && callee.getDeclaringClass() == InterruptableTask.class
                                && callee.getMethodName().equals("run");
                    }
                    currentInvocationSeen = true;
                }
                callee = frame;
            }
            throw new IllegalStateException("Cannot determine InterruptableTask run() dispatch");
        });
    }

    private enum RunImplementation {
        OVERRIDDEN,
        DEFAULT,
        UNRESOLVED
    }

    private static final class RecursiveExecutionError extends Error {
        private static final long serialVersionUID = 1L;

        private RecursiveExecutionError() {
            super(null, null, false, false);
        }
    }
}
