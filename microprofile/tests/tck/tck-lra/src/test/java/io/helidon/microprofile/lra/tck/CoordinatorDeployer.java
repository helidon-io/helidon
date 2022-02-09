/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.config.ConfigSources;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.arquillian.HelidonContainerConfiguration;
import io.helidon.microprofile.arquillian.HelidonDeployableContainer;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.CDI;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class CoordinatorDeployer {

    private static final Logger LOGGER = Logger.getLogger(CoordinatorDeployer.class.getName());

    static final String COORDINATOR_ROUTING_NAME = "coordinator";
    private static volatile CompletableFuture<Void> startedFuture = new CompletableFuture<>();
    private final AtomicInteger coordinatorPort = new AtomicInteger(0);
    private final AtomicInteger clientPort = new AtomicInteger(0);

    public void beforeStart(@Observes BeforeStart event, Container container) throws Exception {
        HelidonDeployableContainer helidonContainer = (HelidonDeployableContainer) container.getDeployableContainer();
        HelidonContainerConfiguration containerConfig = helidonContainer.getContainerConfig();

        containerConfig.addConfigBuilderConsumer(configBuilder -> {
            var is = CoordinatorService.class.getResourceAsStream("/application.yaml");
            configBuilder.withSources(MpConfigSources.create(ConfigSources.create(is, MediaType.APPLICATION_X_YAML.toString())),
                    MpConfigSources.create(Map.of(
                            // Force client to use random port first time with 0
                            // reuse port second time(TckRecoveryTests does redeploy)
                            "server.port", String.valueOf(clientPort.get()),
                            "server.worker-count", "16",
                            "server.sockets.0.name", COORDINATOR_ROUTING_NAME,
                            // Force coordinator to use random port first time with 0
                            // reuse port second time(TckRecoveryTests does redeploy)
                            "server.sockets.0.port", String.valueOf(coordinatorPort.get()),
                            "server.sockets.0.worker-count", "16",
                            "server.sockets.0.bind-address", "localhost",
                            "helidon.lra.coordinator.db.connection.url", "jdbc:h2:file:./target/lra-coordinator",
                            "helidon.lra.coordinator.recovery-interval", "100",
                            "helidon.lra.coordinator.timeout", "3000"
                    )));
        });

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addClass(CoordinatorAppService.class);

        helidonContainer.getAdditionalArchives().add(javaArchive);

    }

    public void afterDeploy(@Observes AfterDeploy event, Container container) throws Exception {
        ServerCdiExtension serverCdiExtension = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class);
        for (int i = 0;
             i < 50 && !serverCdiExtension.started();
             i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        startedFuture.complete(null);
    }

    public void beforeUndeploy(@Observes BeforeUnDeploy event, Container container) {
        startedFuture = new CompletableFuture<>();
        // Gracefully stop the container so coordinator gets the chance to persist lra registry
        try {
            CDI<Object> current = CDI.current();
            // Remember the ports for redeploy in TckRecoveryTests
            ServerCdiExtension serverCdiExtension = current.getBeanManager().getExtension(ServerCdiExtension.class);
            coordinatorPort.set(serverCdiExtension.port(COORDINATOR_ROUTING_NAME));
            clientPort.set(serverCdiExtension.port());
            ((SeContainer) current).close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    static Single<Void> started() {
        return Single.create(startedFuture, true);
    }
}
