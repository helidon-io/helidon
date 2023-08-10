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

import java.util.Objects;
import java.util.function.BiConsumer;

import static java.lang.System.Logger.Level.DEBUG;

abstract class FlowControlImpl implements FlowControl {

    private static final System.Logger LOGGER_INBOUND = System.getLogger(FlowControl.class.getName() + ".ifc");
    private static final System.Logger LOGGER_OUTBOUND = System.getLogger(FlowControl.class.getName() + ".ofc");

    private final int streamId;

    FlowControlImpl(int streamId) {
        this.streamId = streamId;
    }

    abstract WindowSize connectionWindowSize();

    abstract WindowSize streamWindowSize();

    @Override
    public void resetStreamWindowSize(int size) {
        streamWindowSize().resetWindowSize(size);
    }

    @Override
    public int getRemainingWindowSize() {
        return Math.max(0,
                        Integer.min(
                                connectionWindowSize().getRemainingWindowSize(),
                                streamWindowSize().getRemainingWindowSize()
                        )
        );
    }

    @Override
    public String toString() {
        return "FlowControlImpl{"
                + "streamId=" + streamId
                + ", connectionWindowSize=" + connectionWindowSize()
                + ", streamWindowSize=" + streamWindowSize()
                + '}';
    }

    protected int streamId() {
        return this.streamId;
    }

    static class Inbound extends FlowControlImpl implements FlowControl.Inbound {

        private final WindowSize.Inbound connectionWindowSize;
        private final WindowSize.Inbound streamWindowSize;
        private final ConnectionFlowControl.Type type;

        Inbound(ConnectionFlowControl.Type type,
                int streamId,
                int streamInitialWindowSize,
                int streamMaxFrameSize,
                WindowSize.Inbound connectionWindowSize,
                BiConsumer<Integer, Http2WindowUpdate> windowUpdateStreamWriter) {
            super(streamId);
            this.type = type;
            if (streamInitialWindowSize == 0) {
                throw new IllegalArgumentException("Window size in bytes for stream-level flow control was not set.");
            }
            Objects.requireNonNull(connectionWindowSize, "Window size in bytes for connection-level flow control was not set.");
            Objects.requireNonNull(windowUpdateStreamWriter, "Stream-level window update writer was not set.");
            this.connectionWindowSize = connectionWindowSize;
            this.streamWindowSize = WindowSize.createInbound(type,
                                                             streamId,
                                                             streamInitialWindowSize,
                                                             streamMaxFrameSize,
                                                             windowUpdateStreamWriter);
        }

        @Override
        WindowSize connectionWindowSize() {
            return connectionWindowSize;
        }

        @Override
        WindowSize streamWindowSize() {
            return streamWindowSize;
        }

        @Override
        public void decrementWindowSize(int decrement) {
            long strRemaining = streamWindowSize().decrementWindowSize(decrement);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR %d: -%d(%d)", type, streamId(), decrement, strRemaining));
            }
            long connRemaining = connectionWindowSize().decrementWindowSize(decrement);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR 0: -%d(%d)", type, decrement, connRemaining));
            }
        }

        @Override
        public void incrementWindowSize(int increment) {
            long strRemaining = streamWindowSize.incrementWindowSize(increment);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR %d: +%d(%d)", type, streamId(), increment, strRemaining));
            }
            long conRemaining = connectionWindowSize.incrementWindowSize(increment);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR 0: +%d(%d)", type, increment, conRemaining));
            }
        }

    }

    static class Outbound extends FlowControlImpl implements FlowControl.Outbound {

        private final ConnectionFlowControl.Type type;
        private final ConnectionFlowControl connectionFlowControl;
        private final WindowSize.Outbound streamWindowSize;

        Outbound(ConnectionFlowControl.Type type,
                 int streamId,
                 ConnectionFlowControl connectionFlowControl) {
            super(streamId);
            this.type = type;
            this.connectionFlowControl = connectionFlowControl;
            this.streamWindowSize = WindowSize.createOutbound(type, streamId, connectionFlowControl);
        }

        @Override
        WindowSize connectionWindowSize() {
            return connectionFlowControl.outbound();
        }

        @Override
        WindowSize streamWindowSize() {
            return streamWindowSize;
        }

        @Override
        public void decrementWindowSize(int decrement) {
            long strRemaining = streamWindowSize().decrementWindowSize(decrement);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR %d: -%d(%d)", type, streamId(), decrement, strRemaining));
            }

            long connRemaining = connectionWindowSize().decrementWindowSize(decrement);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR 0: -%d(%d)", type, decrement, connRemaining));

            }
        }

        @Override
        public long incrementStreamWindowSize(int increment) {
            long remaining = streamWindowSize.incrementWindowSize(increment);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR %d: +%d(%d)", type, streamId(), increment, remaining));
            }
            connectionFlowControl.outbound().triggerUpdate();
            return remaining;
        }

        @Override
        public Http2FrameData[] cut(Http2FrameData frame) {
            return frame.cut(getRemainingWindowSize());
        }

        @Override
        public void blockTillUpdate() {
            connectionFlowControl.outbound().blockTillUpdate();
            streamWindowSize.blockTillUpdate();
        }

        @Override
        public int maxFrameSize() {
            return connectionFlowControl.maxFrameSize();
        }
    }

}
