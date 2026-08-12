/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
     * Actual protocol used on the wire for this response.
     * <p>
     * The default implementation returns {@code http/1.1} as a best-effort value. Non-Helidon implementations that can
     * use another wire protocol should override this method; otherwise the returned value may be inaccurate.
     *
     * @return protocol identifier, such as {@code http/1.1}, {@code h2}, or {@code h3}
     */
    default String protocolId() {
        return "http/1.1";
    }

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
