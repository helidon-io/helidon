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

package io.helidon.security.providers.common;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceConfig;
import io.helidon.common.configurable.ResourceException;

/**
 * Opens a fresh resource for a resilient load attempt.
 * URI resources use finite connect and read timeouts so a blocked attempt can fail and be observed by fault tolerance.
 */
@Api.Internal
public final class ResilientResource {
    private ResilientResource() {
    }

    /**
     * Open a fresh resource. Non-URI sources use the standard {@link Resource} handling. URI connect and read timeouts
     * are both derived from {@code ioTimeout}; positive sub-millisecond values use one millisecond and values larger
     * than the {@link URLConnection} limit use that limit.
     *
     * @param description safe resource description, which must not contain a URI or credentials
     * @param resourceConfig resource configuration
     * @param ioTimeout timeout for URI connect and read operations
     * @return newly opened resource
     * @throws ResourceException if a URI resource cannot be opened
     */
    public static Resource create(String description, ResourceConfig resourceConfig, Duration ioTimeout) {
        Objects.requireNonNull(description);
        Objects.requireNonNull(resourceConfig);
        Objects.requireNonNull(ioTimeout);
        if (description.isBlank()) {
            throw new IllegalArgumentException("Resource description must not be blank");
        }
        if (ioTimeout.isZero() || ioTimeout.isNegative()) {
            throw new IllegalArgumentException("Resource I/O timeout must be positive");
        }

        URI uri = resourceConfig.uri().orElse(null);
        if (uri == null) {
            return Resource.create(resourceConfig);
        }

        int timeoutMillis = timeoutMillis(ioTimeout);
        try {
            URLConnection connection;
            if (resourceConfig.useProxy() && resourceConfig.proxy().isPresent()) {
                connection = uri.toURL().openConnection(resourceConfig.proxy().orElseThrow());
            } else {
                connection = uri.toURL().openConnection();
            }
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setUseCaches(false);
            return Resource.create(description, connection.getInputStream());
        } catch (IOException e) {
            throw new ResourceException(description + " could not be opened", e);
        }
    }

    private static int timeoutMillis(Duration timeout) {
        long timeoutMillis;
        try {
            timeoutMillis = timeout.toMillis();
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeoutMillis));
    }
}
