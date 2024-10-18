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

package io.helidon.config.metadata.docs;

import java.util.Objects;

/**
 * Runtime exception for problems when generating config documentation.
 */
public class ConfigDocsException extends RuntimeException {
    /**
     * A new exception with customized message.
     *
     * @param message error message
     */
    public ConfigDocsException(String message) {
        super(Objects.requireNonNull(message));
    }

    /**
     * A new exception with customized message and a cause.
     *
     * @param message error message
     * @param cause   cause of this exception
     */
    public ConfigDocsException(String message, Throwable cause) {
        super(Objects.requireNonNull(message), Objects.requireNonNull(cause));
    }
}
