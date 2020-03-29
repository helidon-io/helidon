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
package io.helidon.common.context;

/**
 * Exception related to execution of a task in context.
 */
public class ExecutorException extends RuntimeException {
    /**
     * Create exception with a descriptive message.
     * @param message details about what happened
     */
    public ExecutorException(String message) {
        super(message);
    }

    /**
     * Create exception with a descriptive message and a cause.
     * @param message details about what happened
     * @param cause original exception caught
     */
    public ExecutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
