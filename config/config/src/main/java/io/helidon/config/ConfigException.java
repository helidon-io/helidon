/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

/**
 * Exception is thrown by {@link Config} implementations.
 */
public class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor with the detailed message.
     *
     * @param message the message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Constructor with the detailed message.
     *
     * @param message the message
     * @param cause   the cause
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

}
