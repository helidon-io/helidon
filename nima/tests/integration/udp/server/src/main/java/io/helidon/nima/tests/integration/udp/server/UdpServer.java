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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.nima.udp.UdpClient;
import io.helidon.nima.udp.UdpEndpoint;
import io.helidon.nima.udp.UdpMessage;
import io.helidon.nima.webserver.WebServer;

/**
 * Class UdpServer for performance testing.
 */
public class UdpServer {

    static final int ACK_FREQUENCY = 10;
    static final ByteBuffer ACK = ByteBuffer.wrap("ack".getBytes(StandardCharsets.UTF_8));

    public static void main(String[] args) {
        WebServer webServer = WebServer
                .builder()
                .udp(true)
                .port(8888)
                .udpEndpoint(new EchoService())
                .build();
        webServer.start();
    }

    static class EchoService implements UdpEndpoint {

        Map<UdpClient, AtomicLong> counters = new ConcurrentHashMap<>();

        /**
         * Reads all bytes available in message.
         *
         * @param message the message
         */
        @Override
        public void onMessage(UdpMessage message) {
            try {
                ByteBuffer bb = message.asByteBuffer();
                while (bb.hasRemaining()) {
                    bb.get();       // consume all bytes
                }
                sendAckMaybe(message.udpClient());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Send ACK as a form of primitive flow control.
         *
         * @param client the client
         * @throws IOException if an error occurs
         */
        private void sendAckMaybe(UdpClient client) throws IOException {
            AtomicLong n = counters.computeIfAbsent(client, c -> new AtomicLong());
            if (n.getAndIncrement() % ACK_FREQUENCY == 0) {
                client.sendMessage(ACK);
            }
        }
    }
}
