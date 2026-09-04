/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.quic.frame.ConnectionCloseFrame;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class QuicEndpointLoggingTest {
    @Test
    void connectionCloseSummaryDoesNotExposeReason() {
        ConnectionCloseFrame frame = ConnectionCloseFrame.create(1, 0, "secret");

        assertThat(frame.toString(), containsString("reasonLength=6"));
        assertThat(frame.toString(), not(containsString("secret")));
    }

    @Test
    void rawDatagramLoggingRequiresExplicitUnsafeOptIn() {
        Logger logger = Logger.getLogger(QuicEndpoint.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINER);

        try {
            ByteBuffer payload = ByteBuffer.wrap("secret".getBytes(StandardCharsets.UTF_8));
            InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 443);

            QuicEndpoint.logDatagram(false, "send", "socket connection", peer, payload);

            assertThat(messages.size(), is(1));
            assertThat(messages.getFirst(), containsString("send datagram (6 bytes)"));
            assertThat(messages.getFirst(), not(containsString("secret")));
            assertThat(messages.getFirst(), not(containsString("73 65 63 72 65 74")));

            messages.clear();
            QuicEndpoint.logDatagram(true, "send", "socket connection", peer, payload);

            assertThat(messages.size(), is(2));
            assertThat(messages.stream().anyMatch(message -> message.contains("UNSAFE raw send datagram")), is(true));
            assertThat(messages.stream().anyMatch(message -> message.contains("73 65 63 72 65 74")), is(true));
            assertThat(messages.stream().anyMatch(message -> message.contains("secret")), is(true));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }
}
