/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing.lra;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.helidon.config.Config;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.lra.coordinator.Lra;
import io.helidon.microprofile.lra.CoordinatorLocatorService;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.http.HttpService;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Enables LRA coordinator on another socket of this server with random port.
 */
@ApplicationScoped
public class TestLraCoordinator {

    static final String ROUTING_NAME = "test-lra-coordinator";
    static final String CONTEXT_PATH = "/lra-coordinator";
    private final CompletableFuture<Integer> port = new CompletableFuture<>();
    private final ServerCdiExtension serverCdiExtension;
    private final CoordinatorService coordinatorService;

    @Inject
    TestLraCoordinator(Config config,
                       ServerCdiExtension serverCdiExtension,
                       CoordinatorLocatorService coordinatorLocator) {
        this.serverCdiExtension = serverCdiExtension;
        this.coordinatorService = CoordinatorService.builder()
                .url(this::coordinatorUri)
                .config(config.get(CoordinatorService.CONFIG_PREFIX))
                .build();
        coordinatorLocator.overrideCoordinatorUriSupplier(this::coordinatorUri);
    }

    @Produces
    @ApplicationScoped
    @RoutingName(value = ROUTING_NAME, required = true)
    @RoutingPath(CONTEXT_PATH)
    HttpService coordinatorService() {
        return coordinatorService;
    }

    /**
     * Return test LRA coordinator URL.
     *
     * @return coordinator url
     */
    public URI coordinatorUri() {
        return URI.create("http://localhost:" + awaitPort() + CONTEXT_PATH);
    }

    /**
     * Get LRA by LraId.
     *
     * @param lraId with or without coordinator url prefix.
     * @return lra when registered by text coordinator
     */
    public Lra lra(String lraId) {
        if (lraId == null) {
            return null;
        }
        if (lraId.startsWith(coordinatorUri() + "/")) {
            return coordinatorService.lra(lraId.substring(coordinatorUri().toString().length() + 1));
        }
        return coordinatorService.lra(lraId);
    }

    private void ready(@Observes @Priority(PLATFORM_AFTER + 101) @Initialized(ApplicationScoped.class) Object e, BeanManager b) {
        port.complete(serverCdiExtension.port(ROUTING_NAME));
    }

    private int awaitPort() {
        try {
            return port.get(20, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
