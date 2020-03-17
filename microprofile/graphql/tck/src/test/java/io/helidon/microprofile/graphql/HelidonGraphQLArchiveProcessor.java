/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

/**
 * Creates a deployable unit with all the dependencies.
 */
public class HelidonGraphQLArchiveProcessor
        implements ApplicationArchiveProcessor {
    @Override
    public void process(Archive<?> applicationArchive, TestClass testClass) {
        if (applicationArchive instanceof WebArchive) {
            WebArchive testDeployment = (WebArchive) applicationArchive;

            final File[] dependencies = Maven.resolver()
                    .loadPomFromFile("pom.xml")
                    .resolve("io.helidon.microprofile.graphql:helidon-microprofile-graphql-server")
                    .withTransitivity()
                    .asFile();
            // Make sure it's unique
            Set<File> dependenciesSet = new LinkedHashSet<>(Arrays.asList(dependencies));
            testDeployment.addAsLibraries(dependenciesSet.toArray(new File[] {}));
            // MicroProfile properties
            testDeployment.addAsResource(
                    HelidonGraphQLArchiveProcessor.class.getClassLoader()
                            .getResource("META-INF/microprofile-config.properties"),
                    "META-INF/microprofile-config.properties");
        }
    }
}
