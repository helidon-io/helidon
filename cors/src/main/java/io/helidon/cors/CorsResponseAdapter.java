/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.cors;

import io.helidon.http.HeaderName;

/**
 * <em>Not for use by developers.</em>
 *
 * Minimal abstraction of an HTTP response.
 *
 * <p>
 * Note to implementers: In some use cases, the CORS support code will invoke the {@code header} methods but not {@code ok}
 * or {@code forbidden}. See to it that header values set on the adapter via the {@code header} methods are propagated to the
 * actual response.
 * </p>
 *
 * @param <T> the type of the response wrapped by the adapter
 */
public interface CorsResponseAdapter<T> {

    /**
     * Arranges to add the specified header and value to the eventual response.
     *
     * @param key   header name to add
     * @param value header value to add
     * @return the adapter
     */
    CorsResponseAdapter<T> header(HeaderName key, String value);

    /**
     * Arranges to add the specified header and value to the eventual response.
     *
     * @param key   header name to add
     * @param value header value to add
     * @return the adapter
     */
    CorsResponseAdapter<T> header(HeaderName key, Object value);

    /**
     * Returns a response with the forbidden status and the specified error message, without any headers assigned
     * using the {@code header} methods.
     *
     * @param message error message to use in setting the response status
     * @return the factory
     */
    T forbidden(String message);

    /**
     * Returns a response with only the headers that were set on this adapter and the status set to OK.
     *
     * @return response instance
     */
    T ok();

    /**
     * Returns the status of the response.
     *
     * @return HTTP status code.
     */
    int status();
}
