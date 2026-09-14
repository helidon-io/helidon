/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;

import static java.util.Objects.requireNonNull;

/**
 * Connection listener that logs all exchanged information.
 */
public class Http1LoggingConnectionListener implements Http1ConnectionListener {
    private static final String LOGGER_NAME = Http1LoggingConnectionListener.class.getName();

    private final io.helidon.http.http1.Http1LoggingConnectionListener delegate;

    private Http1LoggingConnectionListener(io.helidon.http.http1.Http1LoggingConnectionListener delegate) {
        this.delegate = delegate;
    }

    /**
     * Create a new listener with a prefix (such as {@code send} and {@code recv}).
     *
     * @param prefix prefix to use when logging, also used as a suffix of logger name, to enable separate configuration
     */
    public Http1LoggingConnectionListener(String prefix) {
        this(io.helidon.http.http1.Http1LoggingConnectionListener.create(logConfig(),
                                                                         prefix));
    }

    /**
     * Create a new instance with the specified configuration and direction.
     *
     * @param logConfig log configuration
     * @param prefix    usually specifying direction (such as send or recv)
     * @return a new server connection listener
     */
    public static Http1LoggingConnectionListener create(HttpLogConfig logConfig, String prefix) {
        requireNonNull(logConfig, "logConfig");
        requireNonNull(prefix, "prefix");
        HttpLogConfig actualLogConfig = logConfig;
        if (logConfig.loggerName().isEmpty()) {
            actualLogConfig = HttpLogConfig.builder(logConfig)
                    .loggerName(LOGGER_NAME)
                    .build();
        }
        return new Http1LoggingConnectionListener(io.helidon.http.http1.Http1LoggingConnectionListener.create(actualLogConfig,
                                                                                                              prefix));
    }

    @Override
    public void data(ConnectionContext ctx, BufferData data) {
        delegate.data(ctx, data);
    }

    @Override
    public void data(ConnectionContext ctx, byte[] bytes, int offset, int length) {
        delegate.data(ctx, bytes, offset, length);
    }

    @Override
    public void prologue(ConnectionContext ctx, HttpPrologue prologue) {
        delegate.prologue(ctx, prologue);
    }

    @Override
    public void headers(ConnectionContext ctx, Headers headers) {
        delegate.headers(ctx, headers);
    }

    @Override
    public void status(ConnectionContext ctx, Status status) {
        delegate.status(ctx, status);
    }

    private static HttpLogConfig logConfig() {
        return HttpLogConfig.builder()
                .loggerName(LOGGER_NAME)
                .build();
    }
}
