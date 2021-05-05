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

package io.helidon.common.configurable;

/**
 * Exception used from {@link Resource} and other resource related components marking an issue with access to a {@link Resource}
 * or instance generated from it.
 */
public class ResourceException extends RuntimeException {
    /**
     * Exception with message only.
     *
     * @param message message
     */
    public ResourceException(String message) {
        super(message);
    }

    /**
     * Exception with message and cause.
     *
     * @param message message
     * @param cause   cause
     */
    public ResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
