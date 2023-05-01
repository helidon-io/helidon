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

package io.helidon.nima.tests.integration.udp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static java.lang.System.Logger.Level.INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UdpBaseTest {
    private static final System.Logger LOGGER = System.getLogger(UdpBaseTest.class.getName());

    void echoMessage(String msg, InetSocketAddress address) throws IOException {
        try (DatagramChannel channel = DatagramChannel.open()) {
            echoMessageOnChannel(msg, channel, address);
        }
    }

    void echoMessageOnChannel(String msg, DatagramChannel channel, InetSocketAddress address) throws IOException {
        channel.send(ByteBuffer.wrap(msg.getBytes(UTF_8)), address);
        LOGGER.log(INFO, "Client SND: " + msg);
        byte[] bytes = new byte[msg.length()];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        InetSocketAddress remote = (InetSocketAddress) channel.receive(buffer);
        String rcv = new String(bytes, UTF_8);
        LOGGER.log(INFO, "Client RCV: " + rcv);
        assertThat(rcv, is(msg));
        assertThat(remote.getHostName(), is("localhost"));
        assertThat(remote.getPort(), is(address.getPort()));
    }
}
