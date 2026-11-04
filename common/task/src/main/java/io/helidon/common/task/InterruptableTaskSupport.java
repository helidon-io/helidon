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

final class InterruptableTaskSupport {
    private static final ClassValue<Boolean> RUN_IMPLEMENTED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("run").getDeclaringClass() != InterruptableTask.class;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("InterruptableTask must expose run()", e);
            }
        }
    };

    private InterruptableTaskSupport() {
    }

    static void run(InterruptableTask<?> task) {
        if (!RUN_IMPLEMENTED.get(task.getClass())) {
            throw new UnsupportedOperationException("InterruptableTask must override run() or call()");
        }
        task.run();
    }
}
