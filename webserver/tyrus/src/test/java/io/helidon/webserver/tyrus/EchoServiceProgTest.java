/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.util.Collections;

import javax.websocket.server.ServerEndpointConfig;

import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.SetUpRoute;
import io.helidon.webserver.testsupport.WebServerTest;

import org.junit.jupiter.api.Test;

/**
 * Class EchoServiceTest.
 */
@WebServerTest
class EchoServiceProgTest extends TyrusSupportBaseTest {
    EchoServiceProgTest(WebServer ws) {
        super(ws);
    }

    @SetUpRoute
    static void routing(Routing.Rules rules) {
        ServerEndpointConfig.Builder builder = ServerEndpointConfig.Builder.create(
                EchoEndpointProg.class, "/echo");
        builder.encoders(Collections.singletonList(UppercaseCodec.class));
        builder.decoders(Collections.singletonList(UppercaseCodec.class));
        routing(rules, builder.build());
    }

    @Test
    void testEchoSingle() throws Exception {
        new EchoClient(uri("tyrus/echo")).echo("One");
    }

    @Test
    void testEchoMultiple() throws Exception {
        new EchoClient(uri("tyrus/echo")).echo("One", "Two", "Three");
    }
}
