/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient;

import java.net.UnknownHostException;

/**
 * Runtime variant of the {@link UnknownHostException} exception.
 */
public class RuntimeUnknownHostException extends RuntimeException {

    /**
     * Create new instance based on the {@link UnknownHostException} exception.
     * @param e unknown host exception
     */
    RuntimeUnknownHostException(UnknownHostException e) {
        super(e);
    }

    /**
     * Create new instance based on the {@link String} message.
     * @param message exception message
     */
    RuntimeUnknownHostException(String message) {
        super(message);
    }
}
