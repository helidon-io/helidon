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
package io.helidon.data.jdbc;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import io.helidon.common.Api;

/**
 * Binds independent database probes for optional integrations.
 */
@Api.Internal
public final class JdbcHealthSupport {
    private JdbcHealthSupport() {
    }

    /**
     * Creates a reusable probe over the actual source resolved by the client.
     * Construction acquires no connection. Each invocation owns a separate acquisition and returns true only after
     * successful execution, traversal, and cleanup. Expected infrastructure failures return false. Fatal errors propagate.
     * The probe never controls transactions or closes the datasource.
     * <p>
     * A supported ambient local transaction retains its association and completion ownership. Failed and foreign
     * contexts remain unsupported. External transaction aware sources must independently establish that acquisition
     * does not enlist in the caller transaction. Pooled sources must invalidate their physical entry when abort is called.
     * The query timeout does not bound acquisition, network operations, or an HTTP request.
     *
     * @param client The existing Helidon JDBC client supplies the resolved source.
     * @param query Trusted SQL must contain one SELECT without bind markers.
     * @param queryTimeout The timeout must contain positive whole seconds within the JDBC integer range.
     * @return The reusable supplier performs a fresh probe on each invocation.
     * @throws NullPointerException A supplied reference is null.
     * @throws IllegalArgumentException The client is unsupported or the query or timeout is invalid.
     */
    public static BooleanSupplier createProbe(JdbcClient client, String query, Duration queryTimeout) {
        Objects.requireNonNull(client, "The JDBC client must not be null.");
        Objects.requireNonNull(query, "The probe query must not be null.");
        Objects.requireNonNull(queryTimeout, "The query timeout must not be null.");
        if (!(client instanceof JdbcClientImpl implementation)) {
            throw new IllegalArgumentException("Independent probes require a Helidon JDBC client.");
        }
        String sql = JdbcHealthQuery.validate(query);
        long seconds = queryTimeout.getSeconds();
        if (queryTimeout.getNano() != 0 || seconds < 1 || seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "The query timeout must contain positive whole seconds within the JDBC integer range.");
        }
        return implementation.createProbe(sql, (int) seconds);
    }
}
