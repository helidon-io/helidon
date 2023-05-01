/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.WebServer;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest(udp = true)
public class EchoServiceTest {

    private final InetSocketAddress address;

    public EchoServiceTest(WebServer webServer) {
        this.address = new InetSocketAddress("localhost", webServer.port());
    }

    @SetUpServer
    static void setUp(WebServer.Builder builder) {
        builder.udpEndpoint(new EchoService());
    }

    @Test
    void testEchoService() throws IOException {
        echoMessage("hello", address);
        echoMessage("how are you?", address);
        echoMessage("good bye", address);
    }

    private void echoMessage(String msg, InetSocketAddress address) throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.send(ByteBuffer.wrap(msg.getBytes(UTF_8)), address);
            byte[] bytes = new byte[msg.length()];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            InetSocketAddress remote = (InetSocketAddress) channel.receive(buffer);
            String rcv = new String(bytes, UTF_8);
            assertThat(rcv, is(msg));
            assertThat(remote.getHostName(), is("localhost"));
            assertThat(remote.getPort(), is(address.getPort()));
        }
    }
}
