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

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.webserver.spi.ServerConnection;

/**
 * Result of a routed HTTP/1 protocol upgrade.
 */
@Api.Internal
public final class Http1UpgradeResult {
    private static final Http1UpgradeResult RESPONDED = new Http1UpgradeResult(Kind.RESPONDED, Optional.empty());

    private final Kind kind;
    private final Optional<ServerConnection> connection;

    private Http1UpgradeResult(Kind kind, Optional<ServerConnection> connection) {
        this.kind = kind;
        this.connection = connection;
    }

    /**
     * Create an upgraded result.
     *
     * @param connection upgraded connection
     * @return upgraded result
     */
    public static Http1UpgradeResult upgraded(ServerConnection connection) {
        return new Http1UpgradeResult(Kind.UPGRADED, Optional.of(Objects.requireNonNull(connection)));
    }

    /**
     * Create a result for an already-sent HTTP response.
     *
     * @return responded result
     */
    public static Http1UpgradeResult responded() {
        return RESPONDED;
    }

    /**
     * Result kind.
     *
     * @return result kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Upgraded connection.
     *
     * @return upgraded connection when {@link #kind()} is {@link Kind#UPGRADED}
     */
    public Optional<ServerConnection> connection() {
        return connection;
    }

    /**
     * Routed upgrade result kind.
     */
    public enum Kind {
        /**
         * Protocol upgrade completed and returned a new connection.
         */
        UPGRADED,
        /**
         * HTTP response was sent and no protocol switch should happen.
         */
        RESPONDED
    }
}
