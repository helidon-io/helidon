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

import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;

/**
 * HTTP/1 protocol upgrader that owns routed handling for its protocol.
 * <p>
 * If {@link #routedUpgrade(ConnectionContext, HttpPrologue, WritableHeaders)}
 * returns an empty optional, HTTP/1 treats the request as not upgraded and
 * continues with ordinary HTTP routing.
 */
@Api.Internal
public interface Http1RoutedUpgrader extends Http1Upgrader {
    /**
     * Prepare an upgrade that should run through HTTP routing filters and route
     * policies before the protocol switch is completed.
     *
     * @param ctx      connection context
     * @param prologue http prologue of the upgrade request
     * @param headers  http headers of the upgrade request
     * @return prepared routed upgrade, or an empty optional to continue with ordinary HTTP routing
     */
    Optional<Http1RoutedUpgrade> routedUpgrade(ConnectionContext ctx,
                                              HttpPrologue prologue,
                                              WritableHeaders<?> headers);
}
