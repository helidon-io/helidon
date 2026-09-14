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

package io.helidon.http.http2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2LoggingFrameListenerTest {
    private static final String LOGGER_NAME = Http2LoggingFrameListener.class.getName() + ".recv";

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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Http2Headers headers = Http2Headers.create(WritableHeaders.create()
                                                               .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                                                               .add(HeaderNames.COOKIE, "session=secret-cookie")
                                                               .add(HeaderNames.CONTENT_TYPE, "text/plain")
                                                               .add(HeaderNames.CONTENT_LENGTH, "12"))
                    .method(Method.GET)
                    .scheme("https")
                    .path("/path?access_token=query-secret#fragment")
                    .authority("example.test");

            Http2LoggingFrameListener.create(HttpLogConfig.create(), "recv")
                    .headers(ctx, 1, headers);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("Authorization:"));
            assertThat(message, containsString("Cookie:"));
            assertThat(message, containsString(":authority: <redacted>"));
            assertThat(message, containsString(":path: /path"));
            assertThat(message, containsString("Content-Type: text/plain"));
            assertThat(message, containsString("Content-Length: 12"));
            assertThat(message, not(containsString("example.test")));
            assertThat(message, not(containsString("Bearer secret-token")));
            assertThat(message, not(containsString("session=secret-cookie")));
            assertThat(message, not(containsString("query-secret")));
            assertThat(message, not(containsString("fragment")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testAuthorityUsesSafeHeaderConfiguration() {
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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Http2Headers headers = Http2Headers.create(WritableHeaders.create()
                                                               .add(HeaderNames.CONTENT_TYPE, "text/plain"))
                    .authority("example.test");

            Http2LoggingFrameListener.create(HttpLogConfig.builder().safeHeaders(Set.of()).build(), "recv")
                    .headers(ctx, 1, headers);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString(":authority: <redacted>"));
            assertThat(message, containsString("Content-Type: <redacted>"));
            assertThat(message, not(containsString("example.test")));
            assertThat(message, not(containsString("Content-Type: text/plain")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testAuthorityCanBeConfiguredSafe() {
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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Http2Headers headers = Http2Headers.create(WritableHeaders.create())
                    .authority("example.test");

            Http2LoggingFrameListener.create(HttpLogConfig.builder()
                                                     .safeHeaders(Set.of(Http2Headers.AUTHORITY_NAME))
                                                     .build(),
                                             "recv")
                    .headers(ctx, 1, headers);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString(":authority: example.test"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceDoesNotLogRawFrameBytes() {
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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");

            Http2LoggingFrameListener.create(HttpLogConfig.create(), "recv")
                    .frame(ctx, 1, BufferData.create("secret"));

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("frame data bytes=6"));
            assertThat(message, not(containsString("secret")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceLogsRawFrameBytesWhenUnsafeEnabled() {
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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");

            Http2LoggingFrameListener.create(HttpLogConfig.builder().unsafeRawData(true).build(), "recv")
                    .frame(ctx, 1, BufferData.create("secret"));

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("73 65 63 72 65 74"));
            assertThat(message, containsString("secret"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void testTraceLogsUnsafeHeadersWhenUnsafeEnabled() {
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
            SocketContext ctx = mock(SocketContext.class, CALLS_REAL_METHODS);
            when(ctx.socketId()).thenReturn("server");
            when(ctx.childSocketId()).thenReturn("connection");
            Http2Headers headers = Http2Headers.create(WritableHeaders.create()
                                                               .add(HeaderNames.AUTHORIZATION, "Bearer secret-token"))
                    .path("/path?access_token=query-secret#fragment");

            Http2LoggingFrameListener.create(HttpLogConfig.builder().unsafeRawData(true).build(), "recv")
                    .headers(ctx, 1, headers);

            assertThat(messages.size(), is(1));
            String message = messages.getFirst();
            assertThat(message, containsString("Authorization: Bearer secret-token"));
            assertThat(message, containsString(":path: /path?access_token=query-secret#fragment"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }
}
