/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Window size container, used with {@link FlowControl}.
 */
abstract class WindowSizeImpl implements WindowSize {

    private static final System.Logger LOGGER_INBOUND = System.getLogger(FlowControl.class.getName() + ".ifc");
    private static final System.Logger LOGGER_OUTBOUND = System.getLogger(FlowControl.class.getName() + ".ofc");

    private final ConnectionFlowControl.Type type;
    private final int streamId;
    private final AtomicInteger remainingWindowSize;
    private int windowSize;

    private WindowSizeImpl(ConnectionFlowControl.Type type, int streamId, int initialWindowSize) {
        this.type = type;
        this.streamId = streamId;
        this.windowSize = initialWindowSize;
        this.remainingWindowSize = new AtomicInteger(initialWindowSize);
    }

    @Override
    public void resetWindowSize(int size) {
        // When the value of SETTINGS_INITIAL_WINDOW_SIZE changes,
        // a receiver MUST adjust the size of all stream flow-control windows that
        // it maintains by the difference between the new value and the old value
        remainingWindowSize.updateAndGet(o -> o + size - windowSize);
        windowSize = size;
        if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
            LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR %d: Recv INITIAL_WINDOW_SIZE %d(%d)",
                                                     type, streamId, windowSize, remainingWindowSize.get()));
        }
    }

    @Override
    public long incrementWindowSize(int increment) {
        int remaining = remainingWindowSize
                .getAndUpdate(r -> r < 0 || MAX_WIN_SIZE - r > increment
                        ? increment + r
                        : MAX_WIN_SIZE);

        return remaining + increment;
    }

    @Override
    public int decrementWindowSize(int decrement) {
        return remainingWindowSize.updateAndGet(operand -> operand - decrement);
    }

    @Override
    public int getRemainingWindowSize() {
        return remainingWindowSize.get();
    }

    @Override
    public String toString() {
        return String.valueOf(remainingWindowSize.get());
    }

    /**
     * Inbound window size container.
     */
    static final class Inbound extends WindowSizeImpl implements WindowSize.Inbound {

        private final Strategy strategy;
        private final ConnectionFlowControl.Type type;
        private final int streamId;

        Inbound(ConnectionFlowControl.Type type,
                int streamId,
                int initialWindowSize,
                int maxFrameSize,
                BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
            super(type, streamId, initialWindowSize);
            this.type = type;
            this.streamId = streamId;
            // Strategy selection based on initialWindowSize and maxFrameSize
            this.strategy = Strategy.create(new Strategy.Context(maxFrameSize, initialWindowSize), streamId, windowUpdateWriter);
        }

        @Override
        public long incrementWindowSize(int increment) {
            // 6.9
            // A receiver MUST treat the receipt of a WINDOW_UPDATE frame
            // with a flow-control window increment of 0 as a stream error
            if (increment > 0) {
                long result = super.incrementWindowSize(increment);
                strategy.windowUpdate(this.type, this.streamId, increment);
                return result;
            }
            return super.getRemainingWindowSize();
        }

    }

    /**
     * Outbound window size container.
     */
    static final class Outbound extends WindowSizeImpl implements WindowSize.Outbound {

        private final Semaphore updatedSemaphore = new Semaphore(1);
        private final ConnectionFlowControl.Type type;
        private final int streamId;
        private final long timeoutMillis;

        Outbound(ConnectionFlowControl.Type type, int streamId, ConnectionFlowControl connectionFlowControl) {
            super(type, streamId, connectionFlowControl.initialWindowSize());
            this.type = type;
            this.streamId = streamId;
            this.timeoutMillis = connectionFlowControl.timeout().toMillis();
        }

        @Override
        public long incrementWindowSize(int increment) {
            long remaining = super.incrementWindowSize(increment);
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                LOGGER_OUTBOUND.log(DEBUG, String.format("%s OFC STR %d: +%d(%d)", type, streamId, increment, remaining));
            }
            triggerUpdate();
            return remaining;
        }

        @Override
        public void resetWindowSize(int size) {
            super.resetWindowSize(size);
            triggerUpdate();
        }

        @Override
        public int decrementWindowSize(int decrement) {
            int n = super.decrementWindowSize(decrement);
            triggerUpdate();
            return n;
        }

        @Override
        public void triggerUpdate() {
            updatedSemaphore.release();
        }

        @Override
        public void blockTillUpdate() {
            var startTime = System.currentTimeMillis();
            while (getRemainingWindowSize() < 1) {
                try {
                    updatedSemaphore.drainPermits();
                    var ignored = updatedSemaphore.tryAcquire(timeoutMillis / 4, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    debugLog("%s OFC STR %d: Window depleted, waiting for update interrupted.", e);
                    throw new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control update wait interrupted.");
                }
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    debugLog("%s OFC STR %d: Window depleted, waiting for update time-out.", null);
                    throw new Http2Exception(Http2ErrorCode.FLOW_CONTROL, "Flow control update wait time-out.");
                }
                debugLog("%s OFC STR %d: Window depleted, waiting for update.", null);
            }
        }

        private void debugLog(String message, Exception e) {
            if (LOGGER_OUTBOUND.isLoggable(DEBUG)) {
                if (e != null) {
                    LOGGER_OUTBOUND.log(DEBUG, String.format(message, type, streamId), e);
                } else {
                    LOGGER_OUTBOUND.log(DEBUG, String.format(message, type, streamId));
                }
            }
        }
    }

    /**
     * Inbound window size container with flow control turned off.
     */
    public static final class InboundNoop implements WindowSize.Inbound {

        private static final int WIN_SIZE_WATERMARK = MAX_WIN_SIZE / 2;
        private final int streamId;
        private final BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter;
        private int delayedIncrement;

        InboundNoop(int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
            this.streamId = streamId;
            this.windowUpdateWriter = windowUpdateWriter;
            this.delayedIncrement = 0;
        }

        @Override
        public long incrementWindowSize(int increment) {
            // Send WINDOW_UPDATE frame joined for at least 1/2 of the maximum space
            delayedIncrement += increment;
            if (delayedIncrement > WIN_SIZE_WATERMARK) {
                windowUpdateWriter.accept(streamId, new Http2WindowUpdate(delayedIncrement));
                delayedIncrement = 0;
            }
            return getRemainingWindowSize();
        }

        @Override
        public void resetWindowSize(int size) {
        }

        @Override
        public int decrementWindowSize(int decrement) {
            return MAX_WIN_SIZE;
        }

        @Override
        public int getRemainingWindowSize() {
            return MAX_WIN_SIZE;
        }

        @Override
        public String toString() {
            return String.valueOf(MAX_WIN_SIZE);
        }

    }

    abstract static class Strategy {

        // Strategy Type to instance mapping array
        private static final StrategyConstructor[] CREATORS = new StrategyConstructor[] {
                Simple::new,
                Bisection::new
        };
        private final Context context;
        private final int streamId;
        private final BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter;

        private Strategy(Context context, int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
            this.context = context;
            this.streamId = streamId;
            this.windowUpdateWriter = windowUpdateWriter;
        }

        // Strategy implementation factory
        private static Strategy create(Context context, int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
            return CREATORS[Type.select(context).ordinal()]
                    .create(context, streamId, windowUpdateWriter);
        }

        abstract void windowUpdate(ConnectionFlowControl.Type type, int increment, int i);

        Context context() {
            return context;
        }

        int streamId() {
            return this.streamId;
        }

        BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter() {
            return windowUpdateWriter;
        }

        private enum Type {
            /**
             * Simple WINDOW_UPDATE strategy.
             * Sends update frames as soon as buffer space is restored.
             */
            SIMPLE,
            /**
             * Buffer space bisection strategy.
             * Sends update frames when at least half of the buffer space is consumed.
             */
            BISECTION;

            private static Type select(Context context) {
                // Bisection strategy requires at least 4 frames to be placed inside window
                return context.maxFrameSize * 4 <= context.initialWindowSize ? BISECTION : SIMPLE;
            }

        }

        private interface StrategyConstructor {
            Strategy create(Context context, int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter);
        }

        private record Context(
                int maxFrameSize,
                int initialWindowSize) {
        }

        /**
         * Simple update strategy.
         * Sends update frames as soon as buffer space is restored.
         */
        private static final class Simple extends Strategy {

            private Simple(Context context, int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
                super(context, streamId, windowUpdateWriter);
            }

            @Override
            void windowUpdate(ConnectionFlowControl.Type type, int streamId, int increment) {
                if (LOGGER_INBOUND.isLoggable(DEBUG)) {
                    LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR %d: Send WINDOW_UPDATE %s", type, streamId, increment));
                }
                windowUpdateWriter().accept(streamId(), new Http2WindowUpdate(increment));
            }

        }

        /**
         * Buffer space bisection strategy.
         * Sends update frames when at least half of the buffer space is consumed.
         */
        private static final class Bisection extends Strategy {

            private final int watermark;
            private int delayedIncrement;

            private Bisection(Context context, int streamId, BiConsumer<Integer, Http2WindowUpdate> windowUpdateWriter) {
                super(context, streamId, windowUpdateWriter);
                this.delayedIncrement = 0;
                this.watermark = context().initialWindowSize() / 2;
            }

            @Override
            void windowUpdate(ConnectionFlowControl.Type type, int streamId, int increment) {
                if (LOGGER_INBOUND.isLoggable(DEBUG)) {
                    LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR %d: Deferred WINDOW_UPDATE %d, total %d, watermark %d",
                                                            type, streamId, increment, delayedIncrement, watermark));
                }
                delayedIncrement += increment;
                if (delayedIncrement > watermark) {
                    if (LOGGER_INBOUND.isLoggable(DEBUG)) {
                        LOGGER_INBOUND.log(DEBUG, String.format("%s IFC STR %d: Send WINDOW_UPDATE %d, watermark %d",
                                                                type, streamId, delayedIncrement, watermark));
                    }
                    windowUpdateWriter().accept(streamId(), new Http2WindowUpdate(delayedIncrement));
                    delayedIncrement = 0;
                }
            }

        }

    }

}
