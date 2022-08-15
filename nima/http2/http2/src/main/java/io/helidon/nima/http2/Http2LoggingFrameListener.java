/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http2;

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
    public void frameHeader(SocketContext ctx, BufferData headerData) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s frame header data%n%s", prefix, headerData.debugDataHex(true));
        }
    }

    @Override
    public void frameHeader(SocketContext ctx, Http2FrameHeader frameHeader) {
        if (logger.isLoggable(DEBUG)) {
            String flagsString = frameHeader.typedFlags().toString();
            ctx.log(logger, DEBUG, "%s %s frame <length=%d, stream_id=%d, flags=%s active_flags=%s>",
                    prefix,
                    frameHeader.type(),
                    frameHeader.length(),
                    frameHeader.streamId(),
                    BufferData.toBinaryString(frameHeader.flags()),
                    flagsString);
        }
    }

    @Override
    public void frame(SocketContext ctx, BufferData data) {
        if (logger.isLoggable(TRACE)) {
            if (data.available() == 0) {
                ctx.log(logger, TRACE, "%s frame data - empty", prefix);
            } else {
                ctx.log(logger, TRACE, "%s frame data, %n%s", prefix, data.debugDataHex(true));
            }
        }
    }

    @Override
    public void frame(SocketContext ctx, Http2Priority priority) {
        ctx.log(logger, DEBUG, "%s (dep_stream_id: %s, weight: %d, exclusive: %s)",
                prefix,
                priority.streamId(),
                priority.weight(),
                priority.exclusive());
    }

    @Override
    public void frame(SocketContext ctx, Http2RstStream rstStream) {
        ctx.log(logger, DEBUG, "%s (rstStream: %s)", prefix, rstStream.errorCode());
    }

    @Override
    public void frame(SocketContext ctx, Http2Settings settings) {
        ctx.log(logger, DEBUG, "%s %s", prefix, settings);
    }

    @Override
    public void frame(SocketContext ctx, Http2Ping ping) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s ping%n%s", prefix, BufferData.create(ping.getBytes()).debugDataHex(true));
        }
    }

    @Override
    public void frame(SocketContext ctx, Http2GoAway go) {
        if (logger.isLoggable(DEBUG)) {
            if (go.errorCode() == Http2ErrorCode.NO_ERROR) {
                ctx.log(logger, DEBUG, "%s (last_stream_id=%d, errorCode=%s)", prefix, go.lastStreamId(), go.errorCode());
            } else {
                ctx.log(logger, DEBUG, "%s (last_stream_id=%d, errorCode=%s)%n%s",
                        prefix,
                        go.lastStreamId(),
                        go.errorCode(),
                        go.details());
            }
        }
    }

    @Override
    public void frame(SocketContext ctx, Http2WindowUpdate windowUpdate) {
        ctx.log(logger, DEBUG, "%s (size_increment=%d)%n", prefix, windowUpdate.windowSizeIncrement());
    }

    @Override
    public void headers(SocketContext ctx, Http2Headers headers) {
        if (logger.isLoggable(TRACE)) {
            ctx.log(logger, TRACE, "%s headers:%n%s", prefix, headers.toString());
        }
    }
}
