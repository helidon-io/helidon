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

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.lra.coordinator.CoordinatorService;
import io.helidon.microprofile.arquillian.HelidonContainerConfiguration;
import io.helidon.microprofile.arquillian.HelidonDeployableContainer;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class CoordinatorDeployer {

    static final String COORDINATOR_ROUTING_NAME = "coordinator";
    static final String LOCAL_COORDINATOR_URL = "http://localhost:8071/lra-coordinator";
    static final String LOCAL_COORDINATOR_PORT = "8071";

    public void beforeStart(@Observes BeforeStart event, Container container) throws Exception {
        HelidonDeployableContainer helidonContainer = (HelidonDeployableContainer) container.getDeployableContainer();
        HelidonContainerConfiguration containerConfig = helidonContainer.getContainerConfig();

        String coordinatorUrl = System.getProperty("lra.coordinator.url", LOCAL_COORDINATOR_URL);
        String port = System.getProperty("lra.coordinator.port", LOCAL_COORDINATOR_PORT);

        containerConfig.addConfigBuilderConsumer(configBuilder -> {
            configBuilder.withSources(MpConfigSources.create(CoordinatorService.class.getResource("/application.yaml")),
                    MpConfigSources.create(Map.of(
                            // LRA agent 
                            "mp.lra.coordinator.url", coordinatorUrl,
                            // LRA coordinator
                            "helidon.lra.coordinator.url", coordinatorUrl,
                            "server.sockets.0.name", COORDINATOR_ROUTING_NAME,
                            "server.sockets.0.port", port,
                            "server.sockets.0.bind-address", "localhost"
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
            ((SeContainer) current).close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
