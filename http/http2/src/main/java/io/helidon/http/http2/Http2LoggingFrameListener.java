/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * HTTP/2 frame listener that logs all calls.
 */
public class Http2LoggingFrameListener implements Http2FrameListener {
    private final String prefix;
    private final System.Logger logger;

    /**
     * Create listener with a prefix.
     *
     * @param prefix log prefix and logging output prefix (such as {@code send} and {@code recv})
     */
    public Http2LoggingFrameListener(String prefix) {
        this.prefix = prefix;
        this.logger = System.getLogger(Http2LoggingFrameListener.class.getName() + "." + prefix);
    }

    @Override
    public void frameHeader(SocketContext ctx, int streamId, BufferData headerData) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s %d: frame header data%n%s", prefix, streamId, headerData.debugDataHex(true));
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
            if (data.available() == 0) {
                ctx.log(logger, TRACE, "%s %d: frame data - empty", prefix, streamId);
            } else {
                ctx.log(logger, TRACE, "%s %d: frame data, %n%s", prefix, streamId, data.debugDataHex(true));
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
            ctx.log(logger, TRACE, "%s %d: ping%n%s", prefix, streamId, BufferData.create(ping.getBytes()).debugDataHex(true));
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2GoAway go) {
        if (logger.isLoggable(DEBUG)) {
            if (go.errorCode() == Http2ErrorCode.NO_ERROR) {
                ctx.log(logger,
                        DEBUG,
                        "%s %d: (last_stream_id=%d, errorCode=%s)",
                        prefix,
                        streamId,
                        go.lastStreamId(),
                        go.errorCode());
            } else {
                ctx.log(logger, DEBUG, "%s %d: (last_stream_id=%d, errorCode=%s)%n%s",
                        prefix,
                        streamId,
                        go.lastStreamId(),
                        go.errorCode(),
                        go.details());
            }
        }
    }

    @Override
    public void frame(SocketContext ctx, int streamId, Http2WindowUpdate windowUpdate) {
        ctx.log(logger, DEBUG, "%s %d: (size_increment=%d)%n", prefix, streamId, windowUpdate.windowSizeIncrement());
    }

    @Override
    public void headers(SocketContext ctx, int streamId, Http2Headers headers) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s %d: headers:%n%s", prefix, streamId, headers.toString());
        }
    }
}
