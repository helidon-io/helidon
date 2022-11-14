/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.creator.impl;

/**
 * Abstraction for logging messages.
 */
public interface Msgr {

    /**
     * log a debug message.
     *
     * @param message the message
     */
    default void debug(String message) {
        debug(message, null);
    }

    /**
     * log a debug message.
     *
     * @param message the message
     * @param t any throwable
     */
    void debug(String message, Throwable t);

    /**
     * log an info message.
     *
     * @param message the message
     */
    void log(String message);

    /**
     * log a warn message.
     *
     * @param message the message
     * @param t any throwable
     */
    void warn(String message, Throwable t);

    /**
     * log an error message.
     *
     * @param message the message
     * @param t any throwable
     */
    void error(String message, Throwable t);

}
