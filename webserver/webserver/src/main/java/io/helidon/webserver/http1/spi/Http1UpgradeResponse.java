/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1.spi;

import io.helidon.common.Api;
import io.helidon.http.Headers;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;

/**
 * HTTP/1 response operations available to a routed protocol upgrade.
 */
@Api.Internal
public interface Http1UpgradeResponse {
    /**
     * Response headers.
     *
     * @return response headers
     */
    ServerResponseHeaders headers();

    /**
     * Send an empty response with the provided status.
     *
     * @param status response status
     */
    void send(Status status);

    /**
     * Send the configured {@code 101 Switching Protocols} response.
     *
     * @param requiredHeaders protocol headers that must be present in the final response
     */
    void sendSwitchingProtocols(Headers requiredHeaders);
}
