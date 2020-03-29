/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import io.helidon.metrics.HelidonMetadata;
import io.helidon.metrics.RegistryFactory;
import io.helidon.microprofile.server.Server;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Class MetricsMpServiceTest.
 */
public class MetricsMpServiceTest {
    protected static Server server;
    protected static MetricRegistry registry;
    protected static Client client = ClientBuilder.newClient();

    private static int port;
    private static String baseUri;

    @BeforeAll
    public static void initializeServer() throws Exception {
        server = Server.builder()
                .addResourceClass(HelloWorldResource.class)
                .host("localhost")
                // choose a random available port
                .port(-1)
                .build();
        server.start();

        registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);

        port = server.port();
        baseUri = "http://localhost:" + port;
    }

    @AfterAll
    public static void terminateServer() {
        server.stop();
    }

    protected String baseUri() {
        return baseUri;
    }

    protected static void registerCounter(String name) {
        Metadata meta = new HelidonMetadata(name,
                                     name,
                                     name,
                                     MetricType.COUNTER,
                                     MetricUnits.NONE);
        registry.counter(meta);
    }

    protected static Counter getCounter(String name) {
        return registry.counter(name);
    }
}
