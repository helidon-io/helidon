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

package io.helidon.common.crypto;

/**
 * Common cryptography exception.
 */
public class CryptoException extends RuntimeException {

    /**
     * Constructor with detailed message.
     *
     * @param message detailed message
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * Constructor with detailed message and wrapped throwable.
     *
     * @param message detailed message
     * @param throwable wrapped throwable
     */
    public CryptoException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
