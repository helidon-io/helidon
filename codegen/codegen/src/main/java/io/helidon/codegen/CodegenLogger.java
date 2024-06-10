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

package io.helidon.codegen;

/**
 * An abstraction for logging code processing and generation events.
 */
public interface CodegenLogger {
    /**
     * Create a new logger backed by {@link java.lang.System.Logger}.
     *
     * @param logger delegate to log all events to
     * @return a new {@link io.helidon.codegen.CodegenLogger} backed by the system logger
     */
    static CodegenLogger create(System.Logger logger) {
        return new SystemLogger(logger);
    }

    /**
     * Log a new codegen event.
     * See {@link io.helidon.codegen.CodegenEvent} for log level mappings.
     *
     * @param event to log
     */
    void log(CodegenEvent event);

    /**
     * Log a new codegen (simple) event.
     * See {@link io.helidon.codegen.CodegenEvent} for log level mappings.
     *
     * @param level   log level to use
     * @param message message to log
     */
    default void log(System.Logger.Level level, String message) {
        log(CodegenEvent.builder()
                    .level(level)
                    .message(message)
                    .build());
    }
}
