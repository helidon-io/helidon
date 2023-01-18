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

package io.helidon.pico.tools;

/**
 * Abstraction for logging messages.
 */
public interface Msgr {

    /**
     * Log a debug message.
     *
     * @param message the message
     */
    void debug(
            String message);

    /**
     * Log a debug message.
     *
     * @param message the message
     * @param t throwable
     */
    void debug(
            String message,
            Throwable t);

    /**
     * Log an info message.
     *
     * @param message the message
     */
    void log(
            String message);

    /**
     * Log a warning.
     *
     * @param message the message
     */
    void warn(
            String message);

    /**
     * Log a warning message.
     *
     * @param message the message
     * @param t throwable
     */
    void warn(
            String message,
            Throwable t);

    /**
     * Log an error message.
     *
     * @param message the message
     * @param t any throwable
     */
    void error(
            String message,
            Throwable t);

}
