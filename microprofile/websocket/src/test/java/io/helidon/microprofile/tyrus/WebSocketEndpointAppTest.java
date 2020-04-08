/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tyrus;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import io.helidon.microprofile.server.RoutingPath;

import io.helidon.microprofile.server.ServerCdiExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A test that uses an {@code EndpointApplication} subclass annotated with
 * {@code @RoutingPath} to register Websocket endpoints. The context for
 * Websocket endpoints is defined by the value of {@code @RoutingPath}.
 */
public class WebSocketEndpointAppTest extends WebSocketBaseTest {

    @BeforeAll
    static void initClass() {
        container = SeContainerInitializer.newInstance()
                .addBeanClasses(EndpointApplication.class)
                .initialize();
    }

    @Override
    public String context() {
        return "/web";
    }

    @Test
    public void testEchoProg() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + context() + "/echoProg");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
    }

    @Dependent
    @RoutingPath("/web")
    public static class EndpointApplication implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpoints) {
            ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(
                    EchoEndpointProg.class, "/echoProg");
            return Collections.singleton(builder.build());
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> endpoints) {
            return Collections.singleton(EchoEndpointAnnot.class);
        }
    }
}
