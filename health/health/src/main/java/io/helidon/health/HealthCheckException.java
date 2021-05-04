/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.health;

/**
 * Exception thrown by Health system if something goes wrong.
 */
public class HealthCheckException extends RuntimeException {
    /**
     * Exception with a message and a cause.
     *
     * @param message descriptive message
     * @param cause   cause of this exception
     */
    public HealthCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
