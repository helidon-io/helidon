/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra.tck;

import java.lang.System.Logger.Level;
import java.net.URI;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.lra.CoordinatorLocatorService;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;
import static java.time.Duration.ofSeconds;

@ApplicationScoped
public class CoordinatorAppService {

    private static final System.Logger LOGGER = System.getLogger(CoordinatorAppService.class.getName());

    @Inject
    Config config;

    @Inject
    ServerCdiExtension serverCdiExtension;

    LazyValue<URI> coordinatorUri = LazyValue.create(() -> {
        CoordinatorDeployer.started().await(ofSeconds(30));
        // Check if external coordinator is set or use internal with random port
        int randomPort = serverCdiExtension.port(CoordinatorDeployer.COORDINATOR_ROUTING_NAME);
        String port = System.getProperty("lra.coordinator.port", String.valueOf(randomPort));
        String urlProperty = System.getProperty("lra.coordinator.url", "");
        // Maven can't set null
        urlProperty = urlProperty.isEmpty() ? "http://localhost:" + port + "/lra-coordinator" : urlProperty;
        LOGGER.log(Level.INFO, "Using LRA Coordinator: " + urlProperty);
        return URI.create(urlProperty);
    });

    LazyValue<CoordinatorService> coordinatorService = LazyValue.create(() -> CoordinatorService.builder()
            .url(() -> coordinatorUri.get())
            .config(config.get(CoordinatorService.CONFIG_PREFIX))
            .build());

    @Inject
    public CoordinatorAppService(CoordinatorLocatorService coordinatorLocatorService) {
        coordinatorLocatorService.overrideCoordinatorUriSupplier(() -> coordinatorUri.get());
    }

    @Produces
    @ApplicationScoped
    @RoutingName(value = CoordinatorDeployer.COORDINATOR_ROUTING_NAME, required = true)
    @RoutingPath("/lra-coordinator")
    public CoordinatorService coordinatorService() {
        return coordinatorService.get();
    }

    private void whenApplicationTerminates(@Observes @Priority(PLATFORM_BEFORE - 10) @BeforeDestroyed(ApplicationScoped.class) final Object event) {
        if (coordinatorService.isLoaded()) {
            coordinatorService.get().shutdown();
        }
    }
}
