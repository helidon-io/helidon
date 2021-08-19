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
 */
package io.helidon.microprofile.lra.tck;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.arquillian.HelidonContainerConfiguration;
import io.helidon.microprofile.arquillian.HelidonDeployableContainer;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class CoordinatorDeployer {

    private static final Logger LOGGER = Logger.getLogger(CoordinatorDeployer.class.getName());

    static final String COORDINATOR_ROUTING_NAME = "coordinator";

    private final AtomicInteger coordinatorPort = new AtomicInteger(0);
    private final AtomicInteger clientPort = new AtomicInteger(0);

    public void beforeStart(@Observes BeforeStart event, Container container) throws Exception {
        HelidonDeployableContainer helidonContainer = (HelidonDeployableContainer) container.getDeployableContainer();
        HelidonContainerConfiguration containerConfig = helidonContainer.getContainerConfig();

        containerConfig.addConfigBuilderConsumer(configBuilder -> {
            configBuilder.withSources(MpConfigSources.create(CoordinatorService.class.getResource("/application.yaml")),
                    MpConfigSources.create(Map.of(
                            // Force client to use random port first time with 0
                            // reuse port second time(TckRecoveryTests does redeploy)
                            "server.port", String.valueOf(clientPort.get()),
                            "server.workers", "16",
                            "server.sockets.0.name", COORDINATOR_ROUTING_NAME,
                            // Force coordinator to use random port first time with 0
                            // reuse port second time(TckRecoveryTests does redeploy)
                            "server.sockets.0.port", String.valueOf(coordinatorPort.get()),
                            "server.sockets.0.workers", "16",
                            "server.sockets.0.bind-address", "localhost",
                            "helidon.lra.coordinator.timeout", "800"
                    )));
        });

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addClass(CoordinatorAppService.class);

        helidonContainer.getAdditionalArchives().add(javaArchive);

    }

    public void beforeUndeploy(@Observes BeforeUnDeploy event, Container container) throws DeploymentException {
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
}
