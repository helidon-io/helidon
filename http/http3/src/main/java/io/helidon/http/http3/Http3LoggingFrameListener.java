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

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.LogFormatter;
import io.helidon.http.WritableHeaders;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static java.util.Objects.requireNonNull;

/**
 * HTTP/3 frame listener that logs protocol events.
 */
@Api.Internal
public final class Http3LoggingFrameListener implements Http3FrameListener {
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");

    private final String prefix;
    private final System.Logger logger;
    private final LogFormatter logFormatter;
    private final boolean unsafeLogRawData;

    private Http3LoggingFrameListener(String prefix,
                                      System.Logger logger,
                                      LogFormatter logFormatter,
                                      boolean unsafeLogRawData) {
        this.prefix = prefix;
        this.logger = logger;
        this.logFormatter = logFormatter;
        this.unsafeLogRawData = unsafeLogRawData;
    }

    /**
     * Create a logging frame listener.
     *
     * @param config HTTP log configuration
     * @param prefix log prefix and logger-name suffix
     * @return logging frame listener
     */
    public static Http3LoggingFrameListener create(HttpLogConfig config, String prefix) {
        requireNonNull(config, "config");
        requireNonNull(prefix, "prefix");
        String loggerName = config.loggerName().orElseGet(Http3LoggingFrameListener.class::getName) + "." + prefix;
        return new Http3LoggingFrameListener(prefix,
                                             System.getLogger(loggerName),
                                             LogFormatter.create(config),
                                             config.unsafeRawData());
    }

    @Override
    public boolean enabled() {
        return logger.isLoggable(DEBUG) || logger.isLoggable(TRACE);
    }

    @Override
    public boolean rawDataEnabled() {
        return unsafeLogRawData && logger.isLoggable(TRACE);
    }

    @Override
    public void frameHeader(SocketContext context,
                            long streamId,
                            long frameType,
                            long frameLength,
                            int encodedLength) {
        if (logger.isLoggable(DEBUG)) {
            log(DEBUG,
                context,
                streamId,
                "%s frame <length=%d>",
                frameTypeName(frameType),
                frameLength);
        }
        if (!unsafeLogRawData && logger.isLoggable(TRACE)) {
            log(TRACE, context, streamId, "frame header bytes=%d", encodedLength);
        }
    }

    @Override
    public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
        requireNonNull(data, "data");
        if (rawDataEnabled()) {
            log(TRACE,
                context,
                streamId,
                "frame header data\n%s",
                BufferData.create(data).debugDataHex(true));
        }
    }

    @Override
    public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
        data(context, streamId, "frame data", byteCount);
    }

    @Override
    public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
        requireNonNull(data, "data");
        rawData(context, streamId, "frame data", data);
    }

    @Override
    public void streamType(SocketContext context, long streamId, Http3StreamType streamType, int encodedLength) {
        requireNonNull(streamType, "streamType");
        if (logger.isLoggable(DEBUG)) {
            log(DEBUG, context, streamId, "uni stream type %s", streamType);
        }
        data(context, streamId, "stream type data", encodedLength);
    }

    @Override
    public void streamData(SocketContext context, long streamId, String label, int byteCount) {
        requireNonNull(label, "label");
        data(context, streamId, label, byteCount);
    }

    @Override
    public void rawStreamData(SocketContext context, long streamId, String label, byte[] data) {
        requireNonNull(label, "label");
        requireNonNull(data, "data");
        rawData(context, streamId, label, data);
    }

    @Override
    public void requestHeaders(SocketContext context,
                               long streamId,
                               String method,
                               String scheme,
                               String authority,
                               String path,
                               Headers headers) {
        requireNonNull(method, "method");
        requireNonNull(scheme, "scheme");
        requireNonNull(authority, "authority");
        requireNonNull(path, "path");
        requireNonNull(headers, "headers");
        System.Logger.Level level = headerLevel();
        if (level != null) {
            log(level,
                context,
                streamId,
                "headers:\n%s",
                requestHeaders(method, scheme, authority, path, headers, level == TRACE && unsafeLogRawData));
        }
    }

    @Override
    public void responseHeaders(SocketContext context, long streamId, int status, Headers headers) {
        requireNonNull(headers, "headers");
        System.Logger.Level level = headerLevel();
        if (level != null) {
            StringBuilder builder = new StringBuilder(":status: ")
                    .append(status)
                    .append('\n')
                    .append(format(headers, level == TRACE && unsafeLogRawData));
            log(level, context, streamId, "headers:\n%s", builder);
        }
    }

    @Override
    public void trailers(SocketContext context, long streamId, Headers trailers) {
        requireNonNull(trailers, "trailers");
        System.Logger.Level level = headerLevel();
        if (level != null) {
            log(level,
                context,
                streamId,
                "trailers:\n%s",
                format(trailers, level == TRACE && unsafeLogRawData));
        }
    }

    private void data(SocketContext context, long streamId, String label, int byteCount) {
        if (!unsafeLogRawData && logger.isLoggable(TRACE)) {
            log(TRACE, context, streamId, "%s bytes=%d", label, byteCount);
        }
    }

    private void rawData(SocketContext context, long streamId, String label, byte[] data) {
        if (!rawDataEnabled()) {
            return;
        }
        if (data.length == 0) {
            log(TRACE, context, streamId, "%s - empty", label);
        } else {
            log(TRACE,
                context,
                streamId,
                "%s,\n%s",
                label,
                BufferData.create(data).debugDataHex(true));
        }
    }

    private System.Logger.Level headerLevel() {
        if (logger.isLoggable(TRACE)) {
            return TRACE;
        }
        if (logger.isLoggable(DEBUG)) {
            return DEBUG;
        }
        return null;
    }

    private String requestHeaders(String method,
                                  String scheme,
                                  String authority,
                                  String path,
                                  Headers headers,
                                  boolean unsafe) {
        StringBuilder builder = new StringBuilder(":method: ")
                .append(LogFormatter.escape(method))
                .append('\n');
        if (!scheme.isEmpty()) {
            builder.append(":scheme: ")
                    .append(LogFormatter.escape(scheme))
                    .append('\n');
        }
        if (!authority.isEmpty()) {
            WritableHeaders<?> authorityHeader = WritableHeaders.create().add(AUTHORITY, authority);
            builder.append(unsafe ? logFormatter.formatAll(authorityHeader) : logFormatter.format(authorityHeader));
        }
        if (!path.isEmpty()) {
            builder.append(":path: ")
                    .append(LogFormatter.escape(unsafe ? path : LogFormatter.pathOnly(path)))
                    .append('\n');
        }
        return builder.append(format(headers, unsafe)).toString();
    }

    private String format(Headers headers, boolean unsafe) {
        return unsafe ? logFormatter.formatAll(headers) : logFormatter.format(headers);
    }

    private void log(System.Logger.Level level,
                     SocketContext context,
                     long streamId,
                     String format,
                     Object... arguments) {
        Object[] actualArguments = new Object[arguments.length + 2];
        actualArguments[0] = prefix;
        actualArguments[1] = streamId;
        System.arraycopy(arguments, 0, actualArguments, 2, arguments.length);
        context.log(logger, level, "%s %d: " + format, actualArguments);
    }

    private static String frameTypeName(long frameType) {
        if (frameType == Http3Protocol.FRAME_DATA) {
            return "DATA";
        }
        if (frameType == Http3Protocol.FRAME_HEADERS) {
            return "HEADERS";
        }
        if (frameType == Http3Protocol.FRAME_SETTINGS) {
            return "SETTINGS";
        }
        if (frameType == Http3Protocol.FRAME_GOAWAY) {
            return "GOAWAY";
        }
        return "0x" + Long.toHexString(frameType);
    }
}
