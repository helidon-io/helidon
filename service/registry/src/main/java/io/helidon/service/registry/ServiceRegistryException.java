/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

/**
 * An exception marking a problem with service registry operations.
 */
public class ServiceRegistryException extends RuntimeException {
    /**
     * Create an exception with a descriptive message.
     *
     * @param message the message
     */
    public ServiceRegistryException(String message) {
        super(message);
    }

    /**
     * Create an exception with a descriptive message and a cause.
     *
     * @param message the message
     * @param cause   throwable causing this exception
     */
    public ServiceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
