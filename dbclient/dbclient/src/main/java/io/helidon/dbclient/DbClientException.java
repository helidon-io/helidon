/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient;

/**
 * A {@link RuntimeException} used by Helidon DB component.
 */
public class DbClientException extends RuntimeException {
    /**
     * Create a new exception for a message.
     * @param message descriptive message
     */
    public DbClientException(String message) {
        super(message);
    }

    /**
     * Create a new exception for a message and a cause.
     *
     * @param message descriptive message
     * @param cause original throwable causing this exception
     */
    public DbClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
