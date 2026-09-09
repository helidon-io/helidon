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

package io.helidon.webserver.tests.http2;

import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2Util;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@ServerTest
class ConnectionIdentificationTest {
    private static final Logger CONNECTION_HANDLER_LOGGER = Logger.getLogger("io.helidon.webserver.ConnectionHandler");

    static {
        LogConfig.configureRuntime();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.get("/", (_, res) -> res.send());
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder().build());
    }

    @Test
    void peerCloseDuringConnectionIdentificationIsTrace(WebServer server) throws Exception {
        try (TestLogHandler logHandler = new TestLogHandler(CONNECTION_HANDLER_LOGGER);
             Socket socket = new Socket("127.0.0.1", server.port())) {
            byte[] preface = Http2Util.prefaceData().readBytes();
            socket.getOutputStream().write(preface, 0, preface.length - 1);
            socket.shutdownOutput();

            LogRecord record = logHandler.await(Duration.ofSeconds(5));
            assertThat("Connection failure was not logged", record, is(notNullValue()));
            assertThat(record.getLevel(), is(Level.FINER));
            assertThat(record.getMessage(), not(containsString("unexpected exception")));
        }
    }

    @Test
    void exactHttp2PrefaceIdentifiesConnection(WebServer server) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(Http2Util.prefaceData().readBytes());
            socket.getOutputStream().flush();

            byte[] frameHeaderBytes = socket.getInputStream().readNBytes(Http2FrameHeader.LENGTH);
            assertThat(frameHeaderBytes.length, is(Http2FrameHeader.LENGTH));
            Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(frameHeaderBytes));
            assertThat(frameHeader.type(), is(Http2FrameType.SETTINGS));
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level originalLevel;
        private final boolean originalUseParentHandlers;
        private final CountDownLatch recordReceived = new CountDownLatch(1);
        private volatile LogRecord record;

        private TestLogHandler(Logger logger) {
            this.logger = logger;
            this.originalLevel = logger.getLevel();
            this.originalUseParentHandlers = logger.getUseParentHandlers();
            setLevel(Level.ALL);
            logger.addHandler(this);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getThrown() instanceof DataReader.InsufficientDataAvailableException) {
                this.record = record;
                recordReceived.countDown();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
        }

        private LogRecord await(Duration timeout) throws InterruptedException {
            return recordReceived.await(timeout.toMillis(), TimeUnit.MILLISECONDS) ? record : null;
        }
    }
}
