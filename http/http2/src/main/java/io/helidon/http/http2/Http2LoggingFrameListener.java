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

package io.helidon.http.http2;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.LogFormatter;
import io.helidon.http.WritableHeaders;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.util.Objects.requireNonNull;

/**
 * HTTP/2 frame listener that logs all calls.
 */
public class Http2LoggingFrameListener implements Http2FrameListener {
    private final String prefix;
    private final System.Logger logger;
    private final LogFormatter logFormatter;
    private final boolean unsafeLogRawData;

    private Http2LoggingFrameListener(String prefix,
                                      System.Logger logger,
                                      LogFormatter logFormatter,
                                      boolean unsafeLogRawData) {
        this.prefix = prefix;
        this.logger = logger;
        this.logFormatter = logFormatter;
        this.unsafeLogRawData = unsafeLogRawData;
    }

    /**
     * Create listener with a prefix.
     *
     * @param prefix log prefix and logging output prefix (such as {@code send} and {@code recv})
     * @deprecated use {@link #create(io.helidon.http.HttpLogConfig, String)} instead
     */
    @Deprecated(forRemoval = true, since = "4.5.0")
    public Http2LoggingFrameListener(String prefix) {
        this(prefix,
             System.getLogger(Http2LoggingFrameListener.class.getName() + "." + prefix),
             LogFormatter.create(HttpLogConfig.create()),
             false);
    }

    /**
     * Create a log listener.
     *
     * @param config log configuration
     * @param prefix prefix
     * @return a new prefixed logging frame listener
     */
    public static Http2LoggingFrameListener create(HttpLogConfig config,
                                                   String prefix) {
        requireNonNull(config, "config");
        requireNonNull(prefix, "prefix");
        var logFormatter = LogFormatter.create(config);
        var logger = config.loggerName().orElseGet(Http2LoggingFrameListener.class::getName) + "." + prefix;
        return new Http2LoggingFrameListener(prefix,
                                             System.getLogger(logger),
                                             logFormatter,
                                             config.unsafeRawData());
    }

    @Override
    public void frameHeader(SocketContext ctx, int streamId, BufferData headerData) {
        if (logger.isLoggable(TRACE)) {
            if (unsafeLogRawData) {
                ctx.log(logger, TRACE, "%s %d: frame header data\n%s", prefix, streamId, headerData.debugDataHex(true));
            } else {
                ctx.log(logger, TRACE, "%s %d: frame header bytes=%d", prefix, streamId, headerData.available());
            }
        }
    }

    @Override
    public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader frameHeader) {
        if (logger.isLoggable(DEBUG)) {
            String flagsString = frameHeader.typedFlags().toString();
            ctx.log(logger, DEBUG, "%s %d: %s frame <length=%d, stream_id=%d, flags=%s active_flags=%s>",
                    prefix,
                    streamId,
                    frameHeader.type(),
                    frameHeader.length(),
                    frameHeader.streamId(),
                    BufferData.toBinaryString(frameHeader.flags()),
                    flagsString);
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, BufferData data) {
        if (logger.isLoggable(TRACE)) {
            if (unsafeLogRawData) {
                if (data.available() == 0) {
                    ctx.log(logger, TRACE, "%s %d: frame data - empty", prefix, streamId);
                } else {
                    ctx.log(logger, TRACE, "%s %d: frame data,\n%s", prefix, streamId, data.debugDataHex(true));
                }
            } else {
                ctx.log(logger, TRACE, "%s %d: frame data bytes=%d", prefix, streamId, data.available());
            }
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2Priority priority) {
        ctx.log(logger, DEBUG, "%s %d: (dep_stream_id: %s, weight: %d, exclusive: %s)",
                prefix,
                streamId,
                priority.streamId(),
                priority.weight(),
                priority.exclusive());
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2RstStream rstStream) {
        ctx.log(logger, DEBUG, "%s %d: (rstStream: %s)", prefix, streamId, rstStream.errorCode());
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2Settings settings) {
        ctx.log(logger, DEBUG, "%s %d: %s", prefix, streamId, settings);
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2Ping ping) {
        if (logger.isLoggable(TRACE)) {
            if (unsafeLogRawData) {
                ctx.log(logger,
                        TRACE,
                        "%s %d: ping\n%s",
                        prefix,
                        streamId,
                        BufferData.create(ping.getBytes()).debugDataHex(true));
            } else {
                ctx.log(logger, TRACE, "%s %d: ping payload bytes=%d", prefix, streamId, ping.data().available());
            }
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2GoAway go) {
        if (logger.isLoggable(DEBUG)) {
            ctx.log(logger, DEBUG, "%s %d: (last_stream_id=%d, errorCode=%s)",
                    prefix,
                    streamId,
                    go.lastStreamId(),
                    go.errorCode());
        }
        if (unsafeLogRawData && logger.isLoggable(TRACE) && go.details() != null && !go.details().isEmpty()) {
            ctx.log(logger,
                    TRACE,
                    "%s %d: goaway details\n%s",
                    prefix,
                    streamId,
                    BufferData.create(go.details().getBytes(StandardCharsets.UTF_8)).debugDataHex(true));
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2WindowUpdate windowUpdate) {
        ctx.log(logger, DEBUG, "%s %d: (size_increment=%d)", prefix, streamId, windowUpdate.windowSizeIncrement());
    }

    @Override
    public void headers(SocketContext ctx, int streamId, Http2Headers headers) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger,
                    TRACE,
                    "%s %d: headers:\n%s",
                    prefix,
                    streamId,
                    format(headers, unsafeLogRawData));
        } else if (logger.isLoggable(DEBUG)) {
            ctx.log(logger,
                    DEBUG,
                    "%s %d: headers:\n%s",
                    prefix,
                    streamId,
                    format(headers, false));
        }
    }

    private static void appendPseudoHeader(StringBuilder builder, String name, String value) {
        builder.append(LogFormatter.escape(name))
                .append(": ")
                .append(LogFormatter.escape(value))
                .append('\n');
    }

    private String format(Http2Headers headers, boolean unsafe) {
        StringBuilder builder = new StringBuilder();
        if (headers.method() != null) {
            appendPseudoHeader(builder, Http2Headers.METHOD, headers.method().text());
        }
        if (headers.scheme() != null) {
            appendPseudoHeader(builder, Http2Headers.SCHEME, headers.scheme());
        }
        if (headers.authority() != null) {
            WritableHeaders<?> authorityHeader = WritableHeaders.create()
                    .add(Http2Headers.AUTHORITY_NAME, headers.authority());
            builder.append(unsafe ? logFormatter.formatAll(authorityHeader)
                                   : logFormatter.format(authorityHeader));
        }
        if (headers.path() != null) {
            appendPseudoHeader(builder,
                               Http2Headers.PATH,
                               unsafe ? headers.path() : LogFormatter.pathOnly(headers.path()));
        }
        if (headers.status() != null) {
            appendPseudoHeader(builder, Http2Headers.STATUS, headers.status().codeText());
        }
        builder.append(unsafe ? logFormatter.formatAll(headers.httpHeaders())
                               : logFormatter.format(headers.httpHeaders()));
        return builder.toString();
    }
}
