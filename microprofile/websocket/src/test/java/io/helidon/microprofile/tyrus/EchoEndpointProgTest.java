/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.nima.websocket.WsCloseCodes;

import jakarta.enterprise.context.Dependent;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(EchoEndpointProg.class)
@AddBean(EchoEndpointProgTest.EndpointApplication.class)
class EchoEndpointProgTest extends EchoEndpointBaseTest {

    @Test
    public void testEchoProg() throws Exception {
        EchoListener listener = new EchoListener();
        URI echoUri = URI.create("ws://localhost:" + serverPort() + "/web/echoProg");
        java.net.http.WebSocket ws = httpClient().newWebSocketBuilder()
                .buildAsync(echoUri, listener)
                .get(WAIT_MILLIS, TimeUnit.SECONDS);

        await(ws.sendText(HELLO_WORLD, true));
        assertThat(listener.awaitEcho(), is(HELLO_WORLD));
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get();
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
            return Collections.emptySet();
        }
    }
}
