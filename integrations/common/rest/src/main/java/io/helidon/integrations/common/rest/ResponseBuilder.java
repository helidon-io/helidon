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

package io.helidon.integrations.common.rest;

import io.helidon.common.Builder;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;

/**
 * Response builder extracted as an interface, to work around the restriction that we cannot
 * have multiple inheritance in Java.
 *
 * @param <B> type of the builder (extending the base builder class)
 * @param <T> type of the target object created by this builder
 * @param <X> type of the entity supported by this builder (such as JsonObject, byte[])
 */
public interface ResponseBuilder<B extends ResponseBuilder<B, T, X>, T, X> extends Builder<T> {
    /**
     * Response status returned by the API call.
     *
     * @param status HTTP status
     * @return updated builder
     */
    B status(Http.ResponseStatus status);

    /**
     * Configure the HTTP headers returned by the API call.
     *
     * @param headers headers
     * @return updated builder
     */
    B headers(Headers headers);

    /**
     * Request ID used when dispatching this request.
     *
     * @param requestId request id
     * @return updated builder
     */
    B requestId(String requestId);

    /**
     * This method is invoked by {@link io.helidon.integrations.common.rest.RestApi} when an entity
     * is received.
     *
     * @param entity entity
     * @return updated builder
     */
    B entity(X entity);
}
