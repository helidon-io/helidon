/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;

/**
 * Response which is created upon receiving of server response.
 */
public interface WebClientServiceResponse {

    /**
     * Received response headers.
     *
     * @return immutable response headers
     */
    WebClientResponseHeaders headers();

    /**
     * Context in which this response is received.
     *
     * @return current context
     */
    Context context();

    /**
     * Status of the response.
     *
     * @return response status
     */
    Http.ResponseStatus status();

}
