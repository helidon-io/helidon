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
import java.util.concurrent.TimeUnit;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.nima.websocket.WsCloseCodes;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(EchoEndpoint.class)
class EchoEndpointTest extends EchoEndpointBaseTest {

    @Test
    public void testEchoAnnot() throws Exception {
        EchoListener listener = new EchoListener();
        URI echoUri = URI.create("ws://localhost:" + serverPort() + "/echoAnnot");
        java.net.http.WebSocket ws = httpClient().newWebSocketBuilder()
                .buildAsync(echoUri, listener)
                .get(WAIT_MILLIS, TimeUnit.SECONDS);

        await(ws.sendText(HELLO_WORLD, true));
        assertThat(listener.awaitEcho(), is(HELLO_WORLD));
        ws.sendClose(WsCloseCodes.NORMAL_CLOSE, "normal").get();
    }
}
