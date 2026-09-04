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

package io.helidon.http.http3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3LoggingFrameListenerTest {
    private static final String LOGGER_NAME = Http3LoggingFrameListener.class.getName() + ".recv";

    @Test
    void testDebugHeaderLoggingMasksUnsafeValues() {
        var headers = WritableHeaders.create()
                .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                .add(HeaderNames.COOKIE, "session=secret-cookie")
                .add(HeaderNames.CONTENT_TYPE, "text/plain")
                .add(HeaderNames.CONTENT_LENGTH, "12");

        List<String> messages = collectMessages(Level.FINE, () ->
                Http3LoggingFrameListener.create(HttpLogConfig.create(), "recv")
                        .requestHeaders(Http3TestSocketContext.INSTANCE,
                                        1,
                                        "GET",
                                        "https",
                                        "example.test",
                                        "/path?access_token=query-secret#fragment",
                                        headers));

        assertThat(messages.size(), is(1));
        String message = messages.getFirst();
        assertThat(message, containsString("[socket connection] recv 1: headers:\n"));
        assertThat(message, containsString(":method: GET"));
        assertThat(message, containsString(":scheme: https"));
        assertThat(message, containsString(":authority: <redacted>"));
        assertThat(message, containsString(":path: /path"));
        assertThat(message, containsString("Content-Type: text/plain"));
        assertThat(message, containsString("Content-Length: 12"));
        assertThat(message, containsString("Authorization: <redacted>"));
        assertThat(message, containsString("Cookie: <redacted>"));
        assertThat(message, not(containsString("example.test")));
        assertThat(message, not(containsString("Bearer secret-token")));
        assertThat(message, not(containsString("session=secret-cookie")));
        assertThat(message, not(containsString("query-secret")));
        assertThat(message, not(containsString("fragment")));
    }

    @Test
    void testAuthorityUsesSafeHeaderConfiguration() {
        HttpLogConfig config = HttpLogConfig.builder()
                .safeHeaders(Set.of(HeaderNames.create(":authority")))
                .build();

        List<String> messages = collectMessages(Level.FINE, () ->
                Http3LoggingFrameListener.create(config, "recv")
                        .requestHeaders(Http3TestSocketContext.INSTANCE,
                                        1,
                                        "GET",
                                        "https",
                                        "example.test",
                                        "/path",
                                        WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "text/plain")));

        assertThat(messages.size(), is(1));
        String message = messages.getFirst();
        assertThat(message, containsString(":authority: example.test"));
        assertThat(message, containsString("Content-Type: <redacted>"));
        assertThat(message, not(containsString("Content-Type: text/plain")));
    }

    @Test
    void testHeaderLoggingEscapesControlCharacters() {
        List<String> messages = collectMessages(Level.FINE, () ->
                Http3LoggingFrameListener.create(HttpLogConfig.create(), "recv")
                        .requestHeaders(Http3TestSocketContext.INSTANCE,
                                        1,
                                        "GET\nforged",
                                        "https",
                                        "example.test",
                                        "/path\r\nforged",
                                        WritableHeaders.create()
                                                .add(HeaderNames.CONTENT_TYPE, "text/plain\nforged")));

        assertThat(messages.size(), is(1));
        String message = messages.getFirst();
        assertThat(message, containsString(":method: GET\\nforged"));
        assertThat(message, containsString(":path: /path\\r\\nforged"));
        assertThat(message, containsString("Content-Type: text/plain\\nforged"));
        assertThat(message, not(containsString("GET\nforged")));
        assertThat(message, not(containsString("text/plain\nforged")));
    }

    @Test
    void testConfiguredLoggerNameAndDirectionPrefix() {
        String baseLoggerName = "io.helidon.http.http3.configured";
        List<String> messages = collectMessages(baseLoggerName + ".cl-send",
                                                Level.FINE,
                                                () -> Http3LoggingFrameListener.create(HttpLogConfig.builder()
                                                                                             .loggerName(baseLoggerName)
                                                                                             .build(),
                                                                                     "cl-send")
                                                        .responseHeaders(Http3TestSocketContext.INSTANCE,
                                                                         3,
                                                                         204,
                                                                         WritableHeaders.create()));

        assertThat(messages.size(), is(1));
        assertThat(messages.getFirst(), containsString("[socket connection] cl-send 3: headers:\n:status: 204"));
    }

    @Test
    void testTraceDoesNotLogRawFrameBytes() {
        List<String> messages = collectMessages(Level.FINER, () ->
                Http3LoggingFrameListener.create(HttpLogConfig.create(), "recv")
                        .frameData(Http3TestSocketContext.INSTANCE,
                                   1,
                                   6,
                                   true));

        assertThat(messages.size(), is(1));
        assertThat(messages.getFirst(), containsString("[socket connection] recv 1: frame data bytes=6"));
        assertThat(messages.getFirst(), not(containsString("secret")));
    }

    @Test
    void testFrameHeaderLoggingUsesActualWireBytesOnlyWhenUnsafe() {
        byte[] nonMinimalHeader = new byte[] {0x40, 0x00, (byte) 0x80, 0x00, 0x00, 0x03};
        Http3LoggingFrameListener safe = Http3LoggingFrameListener.create(HttpLogConfig.create(), "recv");
        List<String> safeMessages = collectMessages(Level.FINER, () -> {
            assertThat(safe.rawDataEnabled(), is(false));
            safe.frameHeader(Http3TestSocketContext.INSTANCE, 1, Http3Protocol.FRAME_DATA, 3, nonMinimalHeader.length);
            safe.rawFrameHeader(Http3TestSocketContext.INSTANCE, 1, nonMinimalHeader);
        });

        assertThat(safeMessages.size(), is(2));
        assertThat(safeMessages.get(1), containsString("frame header bytes=6"));
        assertThat(safeMessages.get(1), not(containsString("40 00 80 00 00 03")));

        Http3LoggingFrameListener unsafe = Http3LoggingFrameListener.create(HttpLogConfig.builder()
                                                                                     .unsafeRawData(true)
                                                                                     .build(),
                                                                             "recv");
        List<String> unsafeMessages = collectMessages(Level.FINER, () -> {
            assertThat(unsafe.rawDataEnabled(), is(true));
            unsafe.frameHeader(Http3TestSocketContext.INSTANCE, 1, Http3Protocol.FRAME_DATA, 3, nonMinimalHeader.length);
            unsafe.rawFrameHeader(Http3TestSocketContext.INSTANCE, 1, nonMinimalHeader);
        });

        assertThat(unsafeMessages.size(), is(2));
        assertThat(unsafeMessages.get(1), containsString("40 00 80 00 00 03"));
    }

    @Test
    void testTraceLogsRawDataAndHeadersWhenUnsafeEnabled() {
        Http3LoggingFrameListener listener = Http3LoggingFrameListener.create(HttpLogConfig.builder()
                                                                                   .unsafeRawData(true)
                                                                                   .build(),
                                                                           "recv");
        var headers = WritableHeaders.create()
                .add(HeaderNames.AUTHORIZATION, "Bearer secret-token");

        List<String> messages = collectMessages(Level.FINER, () -> {
            listener.rawFrameData(Http3TestSocketContext.INSTANCE,
                                  1,
                                  "secret".getBytes(StandardCharsets.UTF_8),
                                  true);
            listener.requestHeaders(Http3TestSocketContext.INSTANCE,
                                    1,
                                    "GET",
                                    "https",
                                    "example.test",
                                    "/path?access_token=query-secret#fragment",
                                    headers);
        });

        assertThat(messages.size(), is(2));
        assertThat(messages.get(0), containsString("73 65 63 72 65 74"));
        assertThat(messages.get(0), containsString("secret"));
        assertThat(messages.get(1), containsString(":authority: example.test"));
        assertThat(messages.get(1), containsString(":path: /path?access_token=query-secret#fragment"));
        assertThat(messages.get(1), containsString("Authorization: Bearer secret-token"));
    }

    @Test
    void testMultilineDiagnosticLogsUseLf() {
        Http3LoggingFrameListener listener = Http3LoggingFrameListener.create(HttpLogConfig.builder()
                                                                                   .unsafeRawData(true)
                                                                                   .build(),
                                                                           "recv");
        List<String> messages = collectMessages(Level.FINER, () -> {
            listener.frameHeader(Http3TestSocketContext.INSTANCE, 1, Http3Protocol.FRAME_DATA, 6, 2);
            listener.rawFrameHeader(Http3TestSocketContext.INSTANCE, 1, new byte[] {0x00, 0x06});
            listener.rawFrameData(Http3TestSocketContext.INSTANCE,
                                  1,
                                  "secret".getBytes(StandardCharsets.UTF_8),
                                  true);
            listener.responseHeaders(Http3TestSocketContext.INSTANCE,
                                     1,
                                     200,
                                     WritableHeaders.create().add(HeaderNames.CONTENT_TYPE, "text/plain"));
        });

        assertThat(messages.size(), is(4));
        assertThat(messages.get(1), containsString("frame header data\n"));
        assertThat(messages.get(2), containsString("frame data,\n"));
        assertThat(messages.get(3), containsString("headers:\n"));
        messages.forEach(message -> assertThat(message, not(containsString("\r"))));
    }

    private static List<String> collectMessages(Level level, Runnable task) {
        return collectMessages(LOGGER_NAME, level, task);
    }

    private static List<String> collectMessages(String loggerName, Level level, Runnable task) {
        Logger logger = Logger.getLogger(loggerName);
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
        logger.setLevel(level);

        try {
            task.run();
            return messages;
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }
}
