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

package io.helidon.nima.tests.integration.udp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.udp.UdpMessage;
import io.helidon.nima.webserver.WebServer;
import org.junit.jupiter.api.Test;

import static java.lang.System.Logger.Level.INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest(udp = true)
class UdpEndpointTest {
    private static final System.Logger LOGGER = System.getLogger(UdpEndpointTest.class.getName());

    private final WebServer webServer;

    public UdpEndpointTest(WebServer webServer) {
        this.webServer = webServer;
    }

    @SetUpServer
    static void setUp(WebServer.Builder builder) {
        builder.udpEndpoint(new EchoService());
    }

    @Test
    void testEndpoint() throws Exception {
        echoMessage("hello");
        echoMessage("how are you?");
        echoMessage("good bye");
    }

    static class EchoService implements UdpEndpoint {

        @Override
        public void onMessage(UdpMessage message) {
            try {
                String str = message.as(String.class);
                LOGGER.log(INFO, "Server RCV: " + str);
                message.udpClient().sendMessage(str);
                LOGGER.log(INFO, "Server SND: " + str);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void echoMessage(String msg) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", webServer.port());
        try (DatagramChannel ch = DatagramChannel.open()) {
            ch.send(ByteBuffer.wrap(msg.getBytes(UTF_8)), address);
            LOGGER.log(INFO, "Client SND: " + msg);
            byte[] bytes = new byte[msg.length()];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            InetSocketAddress remote = (InetSocketAddress) ch.receive(buffer);
            String rcv = new String(bytes, UTF_8);
            LOGGER.log(INFO, "Client RCV: " + rcv);
            assertThat(rcv, is(msg));
            assertThat(remote.getHostName(), is("localhost"));
            assertThat(remote.getPort(), is(webServer.port()));
        }
    }
}
