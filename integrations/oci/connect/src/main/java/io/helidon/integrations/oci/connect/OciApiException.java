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

package io.helidon.integrations.oci.connect;

import io.helidon.integrations.common.rest.ApiException;

/**
 * OCI integration exception.
 * This exception is used when the API invocation fails before we receive an HTTP
 * response.
 * {@link io.helidon.integrations.oci.connect.OciRestException} is used otherwise.
 */
public class OciApiException extends ApiException {
    /**
     * Exception without a message and cause.
     */
    public OciApiException() {
        super();
    }

    /**
     * Exception with message.
     *
     * @param message message
     */
    public OciApiException(String message) {
        super(message);
    }

    /**
     * Exception with message and cause.
     *
     * @param message message
     * @param cause cause
     */
    public OciApiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Exception with cause and no message.
     *
     * @param cause cause
     */
    public OciApiException(Throwable cause) {
        super(cause);
    }
}
