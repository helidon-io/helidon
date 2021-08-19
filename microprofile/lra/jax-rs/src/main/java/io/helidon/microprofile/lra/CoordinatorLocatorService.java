/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.lra.coordinator.client.CoordinatorClient;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_DEFAULT_COORDINATOR_URL;
import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_TIMEOUT;
import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_TIMEOUT_UNIT;
import static io.helidon.lra.coordinator.client.CoordinatorClient.CONF_KEY_COORDINATOR_URL;

/**
 * Service for locating of proper coordinator client.
 */
public class CoordinatorLocatorService {

    private final Optional<String> clientFqdn;
    private final Long coordinatorTimeout;
    private final TimeUnit coordinatorTimeoutUnit;
    private Supplier<URI> coordinatorUriSupplier;

    @Inject
    CoordinatorLocatorService(@ConfigProperty(name = "mp.lra.coordinator.client") Optional<String> clientFqdn,
                              @ConfigProperty(name = CONF_KEY_COORDINATOR_URL, defaultValue = CONF_DEFAULT_COORDINATOR_URL)
                                      String coordinatorUrl,
                              @ConfigProperty(name = CONF_KEY_COORDINATOR_TIMEOUT, defaultValue = "10")
                                      Long coordinatorTimeout,
                              @ConfigProperty(name = CONF_KEY_COORDINATOR_TIMEOUT_UNIT, defaultValue = "SECONDS")
                                      TimeUnit coordinatorTimeoutUnit) {
        this.clientFqdn = clientFqdn;
        this.coordinatorUriSupplier = () -> URI.create(coordinatorUrl);
        this.coordinatorTimeout = coordinatorTimeout;
        this.coordinatorTimeoutUnit = coordinatorTimeoutUnit;
    }

    /**
     * Override standard supplier for getting coordinator uri from config.
     *
     * @param uriSupplier used for locating of coordinator
     */
    public void overrideCoordinatorUriSupplier(Supplier<URI> uriSupplier) {
        coordinatorUriSupplier = uriSupplier;
    }

    @Produces
    @ApplicationScoped
    CoordinatorClient coordinatorClient() {
        List<CoordinatorClient> candidates = HelidonServiceLoader.create(ServiceLoader.load(CoordinatorClient.class)).asList();

        if (candidates.isEmpty()) {
            throw new DeploymentException("No coordinator adapter found");
        }

        if (candidates.size() > 1) {
            throw new DeploymentException("Ambiguous coordinator adapter candidates found: " + candidates.stream()
                    .map(CoordinatorClient::getClass)
                    .map(Class::getName)
                    .collect(Collectors.joining(","))
            );
        }

        CoordinatorClient client = candidates.stream().findFirst().get();

        if (clientFqdn.isPresent()) {
            Optional<CoordinatorClient> selectedClient = candidates.stream()
                    .filter(c -> c.getClass().getName().equals(clientFqdn.get()))
                    .findFirst();

            client = selectedClient.orElseThrow(() -> new DeploymentException("Configured coordinator adapter "
                    + clientFqdn.get()
                    + " not found."));
        }

        client.init(() -> coordinatorUriSupplier.get(), coordinatorTimeout, coordinatorTimeoutUnit);

        return client;
    }
}
