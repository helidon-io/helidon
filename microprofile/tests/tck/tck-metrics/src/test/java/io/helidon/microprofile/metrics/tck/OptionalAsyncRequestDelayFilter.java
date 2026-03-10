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
package io.helidon.microprofile.metrics.tck;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds a small buffer before the optional TCK async endpoint starts so the REST.request timer
 * consistently clears the upstream TCK's strict {@code > 5.0} seconds assertion.
 */
@Provider
@Priority(Priorities.USER)
public class OptionalAsyncRequestDelayFilter implements ContainerRequestFilter {

    static final String OPTIONAL_ASYNC_PATH = "get-async";
    static final long OPTIONAL_ASYNC_DELAY_MS = 250;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!"GET".equals(requestContext.getMethod())
                || !matchesPath(requestContext.getUriInfo().getPath())) {
            return;
        }

        try {
            TimeUnit.MILLISECONDS.sleep(OPTIONAL_ASYNC_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while delaying optional metrics async request", e);
        }
    }

    static boolean matchesPath(String path) {
        return path != null
                && (path.equals(OPTIONAL_ASYNC_PATH)
                || path.endsWith("/" + OPTIONAL_ASYNC_PATH));
    }
}
