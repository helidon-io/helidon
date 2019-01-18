/*
 * Copyright (c) 2018,2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.encryption;

/**
 * Secure config related exception.
 */
public class ConfigEncryptionException extends RuntimeException {

    /**
     * Constructs a new config encryption exception with the specified detail message.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public ConfigEncryptionException(String message) {
        super(message);
    }

    /**
     * Construct with a message.
     *
     * @param message message with descriptive information about the failure
     * @param cause   cause of this exception
     * @see Exception#Exception(String, Throwable)
     */
    public ConfigEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
