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

package io.helidon.webserver.http.spi;

import io.helidon.common.Api;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Handler that can enforce route policy for an HTTP/1 protocol upgrade request
 * without invoking the route endpoint handler.
 * <p>
 * Implementations must call {@link ServerResponse#next()} to allow the protocol
 * switch to continue, send a response to reject the switch, or reroute the
 * request back to ordinary HTTP routing. Ordinary HTTP endpoint handlers are
 * intentionally not invoked while routing upgrade policy.
 */
@Api.Internal
public interface ProtocolUpgradeHandler extends Handler {
    /**
     * Handle an HTTP/1 protocol upgrade request before the protocol switch.
     * The handler follows the same response contract as ordinary routing: call
     * {@link ServerResponse#next()} to allow the protocol switch, send a
     * response to reject the switch, or reroute the request back to ordinary
     * HTTP routing.
     *
     * @param req server request
     * @param res server response
     * @throws Exception in case of a processing error
     */
    void handleProtocolUpgrade(ServerRequest req, ServerResponse res) throws Exception;
}
