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

package io.helidon.webserver.http1;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1LoggingConnectionListenerTest {
    private static final String LOGGER_NAME = Http1LoggingConnectionListener.class.getName() + ".recv";

    @Test
    void testDebugHeaderLoggingMasksUnsafeValues() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
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
        logger.setLevel(Level.FINE);

        try {
            ConnectionContext ctx = mock(ConnectionContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Headers headers = WritableHeaders.create()
                    .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                    .add(HeaderNames.COOKIE, "session=secret-cookie")
                    .add(HeaderNames.CONTENT_TYPE, "text/plain")
                    .add(HeaderNames.CONTENT_LENGTH, "12");

            new Http1LoggingConnectionListener("recv").headers(ctx, headers);

            assertThat(messages.size(), is(1));
            String message = messages.get(0);
            assertThat(message, containsString("Authorization:"));
            assertThat(message, containsString("Cookie:"));
            assertThat(message, containsString("Content-Type: text/plain"));
            assertThat(message, containsString("Content-Length: 12"));
            assertThat(message, not(containsString("Bearer secret-token")));
            assertThat(message, not(containsString("session=secret-cookie")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceHeaderLoggingMasksUnsafeValues() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
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
            ConnectionContext ctx = mock(ConnectionContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Headers headers = WritableHeaders.create()
                    .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                    .add(HeaderNames.create("X-Safe"), "first\r\nForged: value");

            Http1LoggingConnectionListener.create(HttpLogConfig.builder()
                                                          .safeHeaders(Set.of(HeaderNames.create("x-safe")))
                                                          .build(),
                                                  "recv")
                    .headers(ctx, headers);

            assertThat(messages.size(), is(1));
            String message = messages.get(0);
            assertThat(message, containsString("Authorization: <redacted>"));
            assertThat(message, containsString("X-Safe: first\\r\\nForged: value"));
            assertThat(message, not(containsString("Bearer secret-token")));
            assertThat(message, not(containsString("\r")));
            assertThat(message, not(containsString("\nForged:")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceDataLoggingReportsByteCount() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
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
            ConnectionContext ctx = mock(ConnectionContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");

            byte[] data = "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            new Http1LoggingConnectionListener("recv").data(ctx, data, 0, data.length);

            assertThat(messages.size(), is(1));
            String message = messages.get(0);
            assertThat(message, containsString("data bytes=6"));
            assertThat(message, not(containsString("secret")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceDataLoggingReportsRawDataWhenUnsafeEnabled() {
        Logger logger = Logger.getLogger(LOGGER_NAME);
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
            ConnectionContext ctx = mock(ConnectionContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");

            byte[] data = "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Http1LoggingConnectionListener.create(HttpLogConfig.builder()
                                                          .safeHeaders(Set.of())
                                                          .unsafeRawData(true)
                                                          .build(),
                                                  "recv")
                    .data(ctx, data, 0, data.length);

            assertThat(messages.size(), is(1));
            String message = messages.get(0);
            assertThat(message, containsString("73 65 63 72 65 74"));
            assertThat(message, containsString("secret"));
            assertThat(message, containsString("data:\n"));
            assertThat(message, not(containsString("\r")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }
}
