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
package io.helidon.nima.http2;

import java.lang.System.Logger.Level;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Window size container, used with {@link io.helidon.nima.http2.FlowControl}.
 */
abstract class WindowSizeImpl implements WindowSize {

    private static final System.Logger LOGGER = System.getLogger(WindowSizeImpl.class.getName());

    private int windowSize;
    private final AtomicInteger remainingWindowSize;

    private WindowSizeImpl(int initialWindowSize) {
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
        LOGGER.log(Level.DEBUG,
                   () -> String.format("Reset window size %d, remaining %d", windowSize, remainingWindowSize.get()));
    }

    @Override
    public boolean incrementWindowSize(int increment) {
        int remaining = remainingWindowSize
                .getAndUpdate(r -> r < 0 || MAX_WIN_SIZE - r > increment
                        ? increment + r
                        : MAX_WIN_SIZE);
        LOGGER.log(Level.DEBUG,
                   () -> String.format("Decrement window size %d, remaining %d", increment, remainingWindowSize.get()));
        return MAX_WIN_SIZE - remaining <= increment;
    }

    @Override
    public void decrementWindowSize(int decrement) {
        remainingWindowSize.updateAndGet(operand -> operand - decrement);
        LOGGER.log(Level.DEBUG,
                   () -> String.format("Decrement window size %d, remaining %d", decrement, remainingWindowSize.get()));
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

        Inbound(int initialWindowSize, int maxFrameSize, Consumer<Http2WindowUpdate> windowUpdateWriter) {
            super(initialWindowSize);
            // Strategy selection based on initialWindowSize and maxFrameSize
            this.strategy = Strategy.create(new Strategy.Context(maxFrameSize, initialWindowSize),
                                            windowUpdateWriter);
        }

        @Override
        public boolean incrementWindowSize(int increment) {
            boolean result = super.incrementWindowSize(increment);
            // 6.9
            // A receiver MUST treat the receipt of a WINDOW_UPDATE frame
            // with a flow-control window increment of 0 as a stream error
            if (increment > 0) {
                strategy.windowUpdate(increment);
            }
            return result;
        }

    }

    /**
     * Outbound window size container.
     */
    static final class Outbound extends WindowSizeImpl implements WindowSize.Outbound {

        private final AtomicReference<CompletableFuture<Void>> updated = new AtomicReference<>(new CompletableFuture<>());

        Outbound(int initialWindowSize) {
            super(initialWindowSize);
        }

        @Override
        public boolean incrementWindowSize(int increment) {
            boolean result = super.incrementWindowSize(increment);
            triggerUpdate();
            return result;
        }

        @Override
        public void triggerUpdate() {
            updated.getAndSet(new CompletableFuture<>()).complete(null);
        }

        @Override
        public void blockTillUpdate() {
            while (getRemainingWindowSize() < 1){
                try {
                    //TODO configurable timeout
                    updated.get().get(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    LOGGER.log(Level.WARNING,
                               () -> String.format("Exception %s caught while waiting for window update: %s",
                                                   e.getClass().getName(),
                                                   e.getMessage()));
                }
            }
        }

    }

    /**
     * Inbound window size container with flow control turned off.
     */
    public static final class InboundNoop implements  WindowSize.Inbound {

        private static final int WIN_SIZE_WATERMARK = MAX_WIN_SIZE / 2;
        private final Consumer<Http2WindowUpdate> windowUpdateWriter;
        private int delayedIncrement;

        InboundNoop(Consumer<Http2WindowUpdate> windowUpdateWriter) {
            this.windowUpdateWriter = windowUpdateWriter;
            this.delayedIncrement = 0;
        }

        @Override
        public boolean incrementWindowSize(int increment) {
            // Send WINDOW_UPDATE frame joined for at least 1/2 of the maximum space
            delayedIncrement += increment;
            if (delayedIncrement > WIN_SIZE_WATERMARK) {
                windowUpdateWriter.accept(new Http2WindowUpdate(delayedIncrement));
                delayedIncrement = 0;
            }
            return true;
        }

        @Override
        public void resetWindowSize(int size) {
        }

        @Override
        public void decrementWindowSize(int decrement) {
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

        private final Context context;
        private final Consumer<Http2WindowUpdate> windowUpdateWriter;

        private Strategy(Context context, Consumer<Http2WindowUpdate> windowUpdateWriter) {
            this.context = context;
            this.windowUpdateWriter = windowUpdateWriter;
        }

        abstract void windowUpdate(int increment);

        Context context() {
            return context;
        }

        Consumer<Http2WindowUpdate>
        windowUpdateWriter() {
            return windowUpdateWriter;
        }

        private interface StrategyConstructor {
            Strategy create(Context context, Consumer<Http2WindowUpdate> windowUpdateWriter);
        }

        // Strategy Type to instance mapping array
        private static final StrategyConstructor[] CREATORS = new StrategyConstructor[] {
                Simple::new,
                Bisection::new
        };

        // Strategy implementation factory
        private static Strategy create(Context context, Consumer<Http2WindowUpdate> windowUpdateWriter) {
            return CREATORS[Type.select(context).ordinal()]
                    .create(context, windowUpdateWriter);
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
                return context.maxFrameSize * 4 <= context.maxWindowsize ? BISECTION : SIMPLE;
            }

        }

        private record Context(
                int maxFrameSize,
                int maxWindowsize) {
        }

        /**
         * Simple update strategy.
         * Sends update frames as soon as buffer space is restored.
         */
        private static final class Simple extends Strategy {

            private Simple(Context context, Consumer<Http2WindowUpdate> windowUpdateWriter) {
                super(context, windowUpdateWriter);
            }

            @Override
            void windowUpdate(int increment) {
                windowUpdateWriter().accept(new Http2WindowUpdate(increment));
                LOGGER.log(Level.DEBUG,
                           () -> String.format("Window update increment %d", increment));
            }
        }

        /**
         * Buffer space bisection strategy.
         * Sends update frames when at least half of the buffer space is consumed.
         */
        private static final class Bisection extends Strategy {

            private int delayedIncrement;

            private final int watermark;

            private Bisection(Context context, Consumer<Http2WindowUpdate> windowUpdateWriter) {
                super(context, windowUpdateWriter);
                this.delayedIncrement = 0;
                this.watermark = context().maxWindowsize() / 2;
            }

            @Override
            void windowUpdate(int increment) {
                delayedIncrement += increment;
                LOGGER.log(Level.DEBUG,
                           () -> String.format("Window update hidden increment %d, total %d, watermark %d",
                                               increment,
                                               delayedIncrement,
                                               watermark));
                if (delayedIncrement > watermark) {
                    windowUpdateWriter().accept(new Http2WindowUpdate(delayedIncrement));
                    LOGGER.log(Level.DEBUG,
                               () -> String.format("Window update real increment %d, watermark %d",
                                                   delayedIncrement,
                                                   watermark));
                    delayedIncrement = 0;
                }
            }

        }

    }

}
