/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.cache;

/**
 * RuntimeException thrown in case of Microstream Cache configuration problems.
 *
 */
public class ConfigException extends RuntimeException {

    /**
     * creates a new ConfigException.
     *
     * @param message exception message
     * @param cause the cause
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * creates a new ConfigException.
     *
     * @param message exception message
     */
    public ConfigException(String message) {
        super(message);
    }

}
