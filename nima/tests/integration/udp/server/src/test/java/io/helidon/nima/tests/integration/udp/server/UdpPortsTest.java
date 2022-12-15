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
import java.nio.channels.DatagramChannel;

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.udp.UdpMessage;
import io.helidon.nima.webserver.WebServer;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static java.lang.System.Logger.Level.INFO;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests multiple UDP ports with different endpoint services and media types.
 */
@ServerTest(udp = true)
class UdpPortsTest extends UdpBaseTest {
    private static final System.Logger LOGGER = System.getLogger(UdpPortsTest.class.getName());

    private final InetSocketAddress address;
    private final InetSocketAddress addressJson;

    public UdpPortsTest(WebServer webServer) {
        this.address = new InetSocketAddress("localhost", webServer.port());
        this.addressJson = new InetSocketAddress("localhost", webServer.port("json"));
    }

    @SetUpServer
    static void setUp(WebServer.Builder builder) {
        builder.udpEndpoint(new EchoService());
        builder.socket("json",
                lc -> lc.host("localhost")
                        .port(0)
                        .udp(true)
                        .udpEndpoint(new EchoServiceJson()));
    }

    @Test
    void testEndpoint() throws Exception {
        echoMessage("hello", address);
        echoMessage("how are you?", address);
        echoMessage("good bye", address);
    }

    @Test
    void testJsonEndpoint() throws Exception {
        echoMessage("{\"msg\":\"hello\"}", addressJson);
    }

    @Test
    void testEndpointConnected() throws Exception {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.connect(address);
            assertThat(channel.isConnected(), is(true));
            echoMessageOnChannel("hello", channel, address);
            echoMessageOnChannel("how are you?", channel, address);
            echoMessageOnChannel("good bye", channel, address);
            channel.disconnect();
            assertThat(channel.isConnected(), is(false));
        }
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

    static class EchoServiceJson implements UdpEndpoint {

        @Override
        public void onMessage(UdpMessage message) {
            try {
                JsonObject json = message.as(JsonObject.class);
                LOGGER.log(INFO, "Server RCV: " + json);
                message.udpClient().sendMessage(json);
                LOGGER.log(INFO, "Server SND: " + json);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
