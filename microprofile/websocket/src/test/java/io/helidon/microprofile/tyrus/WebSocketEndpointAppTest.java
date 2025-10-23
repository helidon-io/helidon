/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.Configuration;

import jakarta.enterprise.context.Dependent;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.Test;

/**
 * A test that uses an {@code EndpointApplication} subclass annotated with
 * {@code @RoutingPath} to register Websocket endpoints. The context for
 * Websocket endpoints is defined by the value of {@code @RoutingPath}.
 */
@Configuration(configSources = "application.yaml")
@AddBean(WebSocketEndpointAppTest.EndpointApplication.class)
@AddBean(WebSocketEndpointAppTest.EndpointApplicationOther.class)
class WebSocketEndpointAppTest extends WebSocketBaseTest {

    @Test
    public void testEchoAnnot() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + "/web/echoAnnot");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
        echoClient.shutdown();
    }

    @Test
    public void testEchoAnnotWithQuery() throws Exception {
        // Tyrus JDK client decodes %20 so we escape % here
        URI echoUri = URI.create("ws://localhost:" + port() + "/web/echoAnnot?foo=bar%2520baz");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
        echoClient.shutdown();
    }

    @Test
    public void testEchoProg() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + port() + "/web/echoProg");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
        echoClient.shutdown();
    }

    @Test
    public void testEchoAnnotOther() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + otherPort() + "/other/echoAnnot");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
        echoClient.shutdown();
    }

    @Test
    public void testEchoProgOther() throws Exception {
        URI echoUri = URI.create("ws://localhost:" + otherPort() + "/other/echoProg");
        EchoClient echoClient = new EchoClient(echoUri);
        echoClient.echo("hi", "how are you?");
        echoClient.shutdown();
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

    @Dependent
    @RoutingPath("/other")
    @RoutingName(value = "other", required = true)
    public static class EndpointApplicationOther implements ServerApplicationConfig {
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
