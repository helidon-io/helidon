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

package io.helidon.http.http1;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Headers;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.HttpPrologue;
import io.helidon.http.LogFormatter;
import io.helidon.http.Status;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.util.Objects.requireNonNull;

/**
 * Connection listener that logs all exchanged information.
 */
@Api.Internal
public class Http1LoggingConnectionListener implements Http1ConnectionListener {
    private final String prefix;
    private final System.Logger logger;
    private final LogFormatter logFormatter;
    private final boolean unsafeLogRawData;

    private Http1LoggingConnectionListener(String prefix,
                                           System.Logger logger,
                                           LogFormatter logFormatter,
                                           boolean unsafeLogRawData) {
        this.prefix = prefix;
        this.logger = logger;
        this.logFormatter = logFormatter;
        this.unsafeLogRawData = unsafeLogRawData;
    }

    /**
     * Create a log connection listener for HTTP/1.1 communication.
     * Logger name is based on the configured logger name, or it uses this class, with the {@code prefix} appended to it.
     * <p>
     * Example:
     * Webclient logger for sent data: {@code io.helidon.http.http1.Http1LoggingConnectionListener.client.send}
     *
     * @param config configuration of the HTTP protocol
     * @param prefix prefix of the log, and suffix of the logger name
     * @return a new connection listener
     */
    public static Http1LoggingConnectionListener create(HttpLogConfig config, String prefix) {
        requireNonNull(config, "config");
        requireNonNull(prefix, "prefix");

        String loggerName = config.loggerName()
                .orElseGet(Http1LoggingConnectionListener.class::getName)
                + "." + prefix;

        return new Http1LoggingConnectionListener(prefix,
                                                  System.getLogger(loggerName),
                                                  LogFormatter.create(config),
                                                  config.unsafeRawData());
    }

    @Override
    public boolean enabled() {
        return logger.isLoggable(TRACE) || logger.isLoggable(DEBUG);
    }

    @Override
    public void prologue(SocketContext ctx, HttpPrologue prologue) {
        if (unsafeLogRawData && logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s prologue: %s",
                    prefix,
                    LogFormatter.escape(prologue.toString()));
        } else if (logger.isLoggable(DEBUG)) {
            ctx.log(logger, DEBUG, "%s prologue: method=%s path=%s protocol=%s/%s",
                    prefix,
                    LogFormatter.escape(prologue.method().text()),
                    LogFormatter.escape(prologue.uriPath().rawPath()),
                    LogFormatter.escape(prologue.protocol()),
                    LogFormatter.escape(prologue.protocolVersion()));
        }
    }

    @Override
    public void headers(SocketContext ctx, Headers headers) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s headers: \n%s",
                    prefix,
                    unsafeLogRawData ? logFormatter.formatAll(headers) : logFormatter.format(headers));
        } else if (logger.isLoggable(DEBUG)) {
            ctx.log(logger, DEBUG, "%s headers: \n%s",
                    prefix,
                    logFormatter.format(headers));
        }
    }

    @Override
    public void status(SocketContext ctx, Status status) {
        ctx.log(logger, DEBUG, "%s status: %s",
                prefix,
                status.codeText());
    }

    @Override
    public void data(SocketContext ctx, BufferData data) {
        if (logger.isLoggable(TRACE)) {
            if (unsafeLogRawData) {
                ctx.log(logger, TRACE, "%s data:%n%s",
                        prefix,
                        data.debugDataHex(true));
            } else {
                ctx.log(logger, TRACE, "%s data bytes=%d",
                        prefix,
                        data.available());
            }
        }
    }

    @Override
    public void data(SocketContext ctx, byte[] data, int position, int length) {
        if (logger.isLoggable(TRACE)) {
            data(ctx, BufferData.create(data, position, length));
        }
    }
}
