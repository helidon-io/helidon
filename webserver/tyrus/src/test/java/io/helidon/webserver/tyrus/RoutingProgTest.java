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

package io.helidon.webserver.tyrus;

import javax.websocket.server.ServerEndpointConfig;
import java.net.URI;
import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Class RoutingProgTest.
 */
public class RoutingProgTest extends TyrusSupportBaseTest {

    @BeforeAll
    public static void startServer() throws Exception {
        ServerEndpointConfig.Builder builder1 = ServerEndpointConfig.Builder.create(
                EchoEndpointProg.class, "/echo");
        builder1.encoders(Collections.singletonList(UppercaseCodec.class));
        builder1.decoders(Collections.singletonList(UppercaseCodec.class));
        ServerEndpointConfig.Builder builder2 = ServerEndpointConfig.Builder.create(
                DoubleEchoEndpointProg.class, "/doubleEcho");
        builder2.encoders(Collections.singletonList(UppercaseCodec.class));
        builder2.decoders(Collections.singletonList(UppercaseCodec.class));
        webServer(true, builder1.build(), builder2.build());
    }

    @Test
    public void testEcho() {
        try {
            URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/echo");
            new EchoClient(uri).echo("One");
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }

    @Test
    public void testDoubleEcho() {
        try {
            URI uri = URI.create("ws://localhost:" + webServer().port() + "/tyrus/doubleEcho");
            new EchoClient(uri, (s1, s2) -> s2.equals(s1 + s1)).echo("One");
        } catch (Exception e) {
            fail("Unexpected exception " + e);
        }
    }
}
