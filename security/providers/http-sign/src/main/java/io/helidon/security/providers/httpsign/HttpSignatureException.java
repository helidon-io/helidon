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

package io.helidon.security.providers.httpsign;

/**
 * Exception from HTTP signatures provider.
 */
public class HttpSignatureException extends SecurityException {
    /**
     * Create a new exception with message.
     * @param message descriptive message
     */
    public HttpSignatureException(String message) {
        super(message);
    }

    /**
     * Create a new exception with a cause.
     * @param e cause
     */
    public HttpSignatureException(Exception e) {
        super(e);
    }
}
