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

package io.helidon.webclient.api;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;

/**
 * Http client response base.
 */
interface ClientResponseBase {
    /**
     * Response status.
     *
     * @return status
     */
    Status status();

    /**
     * Response headers.
     *
     * @return headers
     */
    ClientResponseHeaders headers();

    /**
     * Response trailer headers.
     * Blocks until trailers are available.
     *
     * @throws java.lang.IllegalStateException when invoked before entity is requested
     * @return trailers
     */
    ClientResponseTrailers trailers();

    /**
     * URI of the last request. (after redirection)
     *
     * @return last URI
     */
    ClientUri lastEndpointUri();
}
