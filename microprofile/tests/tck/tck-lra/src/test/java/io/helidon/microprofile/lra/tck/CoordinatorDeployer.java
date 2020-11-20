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

import java.nio.file.Files;
import java.nio.file.Paths;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.arquillian.HelidonContainerConfiguration;
import io.helidon.microprofile.arquillian.HelidonDeployableContainer;

import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.event.container.BeforeStart;
import org.jboss.arquillian.container.spi.event.container.BeforeUnDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class CoordinatorDeployer {

    static final boolean USE_MOCK_COODINATOR;

    static {
        boolean mockAvailable = false;
        try {
            Class.forName("io.helidon.microprofile.lra.coordinator.Coordinator");
            mockAvailable = true;
        } catch (ClassNotFoundException e) {
            // Mock coordinator not on the classpath
        }
        USE_MOCK_COODINATOR = mockAvailable;
    }

    public void beforeStart(@Observes BeforeStart event, Container container) throws Exception {
        HelidonDeployableContainer helidonContainer = (HelidonDeployableContainer) container.getDeployableContainer();
        HelidonContainerConfiguration containerConfig = helidonContainer.getContainerConfig();

        String coordinatorUrl = System.getProperty("lra.coordinator.url", "http://localhost:8070/lra-coordinator");
        containerConfig.set("mp.lra.coordinator.url", coordinatorUrl);

        if (!USE_MOCK_COODINATOR) return;

        Files.deleteIfExists(Paths.get("target/mock-coordinator/lra-registry"));

        containerConfig.set("lra.tck.coordinator.persist", "true");
        containerConfig.set("server.sockets.0.name", "coordinator");
        containerConfig.set("server.sockets.0.port", "8070");
        containerConfig.set("server.sockets.0.bind-address", "localhost");

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addAsManifestResource(new StringAsset(
                        "<beans xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                "       xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\"\n" +
                                "       xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee\n" +
                                "                           http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd\"\n" +
                                "       version=\"2.0\"\n" +
                                "       bean-discovery-mode=\"annotated\">\n" +
                                "</beans>"), "beans.xml");


        helidonContainer.getAdditionalArchives().add(javaArchive);

    }

    public void beforeUndeploy(@Observes BeforeUnDeploy event, Container container) throws DeploymentException {
        if (!USE_MOCK_COODINATOR) return;
        // Gracefully stop the container so mock coordinator gets the chance to persist lra registry
        try {
            CDI<Object> current = CDI.current();
            ((SeContainer) current).close();
        } catch (Throwable t) {
        }
    }
}
