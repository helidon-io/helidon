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

package io.helidon.webserver.http2;

import java.util.Objects;

import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamWriter;

abstract class Http2SubProtocolWriter implements Http2StreamWriter {
    @Override
    public void write(Http2FrameData frame) {
        Objects.requireNonNull(frame);
        boolean reset = frame.header().type() == Http2FrameTypes.RST_STREAM.type();
        boolean terminal = switch (frame.header().type()) {
        case DATA, HEADERS -> (frame.header().flags() & Http2Flag.END_OF_STREAM) != 0;
        case RST_STREAM -> true;
        default -> false;
        };
        if (reset) {
            writeSubProtocolReset(frame, connectionWriter() != null);
            return;
        }
        if (!terminal || connectionWriter() == null) {
            delegate().write(frame);
            if (terminal) {
                closeFromLocal();
            }
            return;
        }

        try {
            delegate().write(frame);
        } catch (RuntimeException | Error e) {
            failPublication();
            throw e;
        }
        terminalFrameWritten();
        cleanupAfterLocalClose();
    }

    @Override
    public void writeData(Http2FrameData frame, FlowControl.Outbound outboundFlowControl) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(outboundFlowControl);
        boolean terminal = (frame.header().flags() & Http2Flag.END_OF_STREAM) != 0;
        Http2ConnectionWriter connectionWriter = connectionWriter();
        if (!terminal || connectionWriter == null) {
            delegate().writeData(frame, outboundFlowControl);
            if (terminal) {
                closeFromLocal();
            }
            return;
        }

        try {
            connectionWriter.writeData(frame, outboundFlowControl, this::terminalFrameWritten);
        } catch (Http2Exception e) {
            throw e;
        } catch (RuntimeException | Error e) {
            failPublication();
            throw e;
        }
        cleanupAfterLocalClose();
    }

    @Override
    public int writeHeaders(Http2Headers http2Headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            FlowControl.Outbound outboundFlowControl) {
        Objects.requireNonNull(http2Headers);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(outboundFlowControl);
        Http2ConnectionWriter connectionWriter = connectionWriter();
        if (!flags.endOfStream() || connectionWriter == null) {
            int written = delegate().writeHeaders(http2Headers, streamId, flags, outboundFlowControl);
            if (flags.endOfStream()) {
                closeFromLocal();
            }
            return written;
        }

        int written;
        try {
            written = connectionWriter.writeHeaders(http2Headers,
                                                    streamId,
                                                    flags,
                                                    outboundFlowControl,
                                                    this::terminalFrameWritten);
        } catch (RuntimeException | Error e) {
            failPublication();
            throw e;
        }
        cleanupAfterLocalClose();
        return written;
    }

    @Override
    public int writeHeaders(Http2Headers http2Headers,
                            int streamId,
                            Http2Flag.HeaderFlags flags,
                            Http2FrameData dataFrame,
                            FlowControl.Outbound outboundFlowControl) {
        Objects.requireNonNull(http2Headers);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(dataFrame);
        Objects.requireNonNull(outboundFlowControl);
        boolean terminal = (dataFrame.header().flags() & Http2Flag.END_OF_STREAM) != 0;
        if (flags.endOfStream() && terminal) {
            throw new IllegalArgumentException("Both HTTP/2 headers and data cannot end the same stream");
        }
        if (flags.endOfStream()) {
            throw new IllegalArgumentException("HTTP/2 headers with END_STREAM cannot be followed by data");
        }
        Http2ConnectionWriter connectionWriter = connectionWriter();
        if (!terminal || connectionWriter == null) {
            int written = delegate().writeHeaders(http2Headers, streamId, flags, dataFrame, outboundFlowControl);
            if (terminal) {
                closeFromLocal();
            }
            return written;
        }

        int written;
        try {
            written = connectionWriter.writeHeaders(http2Headers,
                                                    streamId,
                                                    flags,
                                                    dataFrame,
                                                    outboundFlowControl,
                                                    this::terminalFrameWritten);
        } catch (Http2Exception e) {
            throw e;
        } catch (RuntimeException | Error e) {
            failPublication();
            throw e;
        }
        cleanupAfterLocalClose();
        return written;
    }

    abstract Http2StreamWriter delegate();

    abstract Http2ConnectionWriter connectionWriter();

    abstract void terminalFrameWritten();

    abstract void closeFromLocal();

    abstract void cleanupAfterLocalClose();

    abstract void failPublication();

    abstract void writeSubProtocolReset(Http2FrameData frame, boolean trackedPublication);
}
