/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.etcd.internal.client;

import java.io.IOException;

/**
 * General Etcd client API fail exception.
 */
public class EtcdClientException extends IOException {

    private static final long serialVersionUID = 1L;

    private int httpStatusCode = -1;

    /**
     * Init the exception.
     *
     * @param message message
     */
    public EtcdClientException(String message) {
        super(message);
    }

    /**
     * Init the exception.
     *
     * @param message message
     * @param cause   the exception cause
     */
    public EtcdClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Init the exception.
     *
     * @param message        message
     * @param httpStatusCode unexpected response code
     */
    public EtcdClientException(String message, int httpStatusCode) {
        super(message + "(" + httpStatusCode + ")");
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * Checks if the exception reason was unexpected HTTP response code.
     *
     * @return {@code true} in case the exception reason was unexpected HTTP response code
     */
    public boolean isHttpError() {
        return httpStatusCode != -1;
    }

}
