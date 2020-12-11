/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.media.common.DefaultMediaSupport;
import io.helidon.webserver.utils.SocketHttpClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.netty.buffer.ByteBuf;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataChunkReleaseTest {

    static Logger dataChunkLogger = Logger.getLogger(ByteBufRequestChunk.class.getName());
    static Logger leakDetectorLogger = Logger.getLogger(io.netty.util.ResourceLeakDetector.class.getName());

    static volatile boolean leakIntercepted = false;

    private static String originalLeakDetectionLevel;
    private static String originalLeakDetectionSamplingInterval;
    private static final Handler testHandler = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel() == Level.WARNING &&
                    record.getMessage()
                            .startsWith("LEAK: RequestChunk.release() was not called before it was garbage collected.")) {
                leakIntercepted = true;
            }
            if (record.getLevel() == Level.SEVERE &&
                    record.getMessage()
                            .startsWith("LEAK: ByteBuf.release() was not called before it's garbage-collected.")) {
                leakIntercepted = true;
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    };

    @BeforeAll
    static void beforeAll() {
        originalLeakDetectionLevel = System.getProperty("io.netty.leakDetectionLevel");
        originalLeakDetectionSamplingInterval = System.getProperty("io.netty.leakDetection.samplingInterval");

        System.setProperty("io.netty.leakDetectionLevel", "advanced");
        System.setProperty("io.netty.leakDetection.samplingInterval", "1");

        dataChunkLogger.addHandler(testHandler);
        leakDetectorLogger.addHandler(testHandler);
    }

    @AfterAll
    static void afterAll() {
        setSysProperty("io.netty.leakDetectionLevel", originalLeakDetectionLevel);
        setSysProperty("io.netty.leakDetection.samplingInterval", originalLeakDetectionSamplingInterval);
        dataChunkLogger.removeHandler(testHandler);
        leakDetectorLogger.removeHandler(testHandler);
    }

    @BeforeEach
    void setUp() {
        leakIntercepted = false;
    }

    @Test
    void leakMessageChunkConsistencyTest() {
        ByteBufRequestChunk.logLeak();
        assertTrue(leakIntercepted, "Leak message not aligned with test");
    }

    @Test
    void leakMessageNettyDetectorConsistencyTest() {
        TestLeakDetector.logLeak();
        assertTrue(leakIntercepted, "Leak message not aligned with test");
    }

    @Test
    void unconsumedChunksReleaseTest() {
        WebServer server = null;
        try {
            server = WebServer.builder(
                    Routing.builder()
                            .get((req, res) -> {
                                System.gc();
                                res.send("OK");
                            })
                            .build())
                    .addReader(DefaultMediaSupport.stringReader())
                    .build()
                    .start()
                    .await(2, TimeUnit.SECONDS);


            for (int i = 0; i < 30; i++) {
                assertThat("Unexpected response", get(" ", server), is(endsWith("OK")));
            }

        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
        assertFalse(leakIntercepted, "Chunk was not released!");
    }

    private String get(String content, WebServer server) {
        try {
            return SocketHttpClient.sendAndReceive("/", Http.Method.GET, content, server);
        } catch (Exception e) {
            fail("Error when sending test GET request", e);
            return null;
        }
    }

    private static void setSysProperty(String key, String nullableValue) {
        Optional.ofNullable(nullableValue)
                .ifPresentOrElse(s -> System.setProperty(key, s),
                        () -> System.clearProperty(key));
    }


    private static class TestLeakDetector extends ResourceLeakDetector<ByteBuf> {
        private TestLeakDetector() {
            super(ByteBuf.class, 1);
        }

        private static void logLeak() {
            new TestLeakDetector().reportTracedLeak(StringUtil.simpleClassName(ByteBuf.class), "");
        }
    }
}
