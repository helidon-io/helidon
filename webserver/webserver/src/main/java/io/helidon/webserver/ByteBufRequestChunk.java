/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;

import io.netty.buffer.ByteBuf;

/**
 * A {@link DataChunk} implementation that wraps {@link ByteBuf} and invokes
 * {@link ByteBuf#release()} during {@link DataChunk#release()}.
 */
class ByteBufRequestChunk implements DataChunk {
    private static final boolean IS_GRAAL_VM = Boolean.getBoolean("com.oracle.graalvm.isaot");
    private static final Logger LOGGER = Logger.getLogger(ByteBufRequestChunk.class.getName());
    private static final AtomicLong ID_INCREMENTER = new AtomicLong(1);

    private final long id = ID_INCREMENTER.getAndIncrement();
    private final ByteBuffer[] byteBuffers;
    private final ReferenceHoldingQueue.ReleasableReference<DataChunk> ref;

    ByteBufRequestChunk(ByteBuf byteBuf, ReferenceHoldingQueue<DataChunk> referenceHoldingQueue) {

        Objects.requireNonNull(byteBuf, "The ByteBuf must not be null!");
        byteBuffers = new ByteBuffer[] {byteBuf.nioBuffer().asReadOnlyBuffer()};
        ref = new ReferenceHoldingQueue.ReleasableReference<>(this, referenceHoldingQueue, byteBuf::release);
        byteBuf.retain();
    }

    @Override
    public boolean isReleased() {
        return ref.isReleased();
    }

    @Override
    public ByteBuffer[] data() {
        if (isReleased()) {
            throw new IllegalStateException("The request chunk was already released!");
        }
        return byteBuffers;
    }

    @Override
    public void release() {
        ref.release();
    }

    @Override
    public long id() {
        return id;
    }

    /**
     * If possible, release this chunk as part of the finalization rather than
     * through the reference queue (see {@link ReferenceHoldingQueue#release()}
     * and from where it is called). Releasing the underlying {@link ByteBuf} as
     * part of the finalization has a lower memory demand and performs slightly
     * better under a heavy load.
     */
    @SuppressWarnings("checkstyle:NoFinalizer")
    @Override
    protected void finalize() {
        if (!isReleased()) {
            OneTimeLoggerHolder.logOnce();
            release();
        }
    }

    private static void logLeak() {
        // TODO add a link to a website that explains the problem
        LOGGER.warning("LEAK: RequestChunk.release() was not called before it was garbage collected. "
                               + "While the Reactive WebServer is "
                               + "designed to automatically release all the RequestChunks, it still "
                               + "comes with a considerable performance penalty and a demand for a large "
                               + "memory space (depending on expected throughput, it might require even more than 2GB). "
                               + "As such the users are "
                               + "strongly advised to release all the RequestChunk instances "
                               + "explicitly when they're not needed.");
    }

    /**
     * An implementation of {@link ReferenceHoldingQueue} that logs a warning
     * message once and only once.
     */
    static class RefHoldingQueue extends ReferenceHoldingQueue<DataChunk> {

        @Override
        protected void hookOnAutoRelease() {
            OneTimeLoggerHolder.logOnce();
        }
    }

    // one time logger is designed to produce a warning only and only once in the JVM run
    static class OneTimeLoggerHolder {
        private static final AtomicBoolean LOGGED = new AtomicBoolean();

        static {
            if (!IS_GRAAL_VM) {
                logLeak();
            }
        }

        private OneTimeLoggerHolder() {
        }

        /**
         * Trigger initialization of this class and a consequent call of the
         * static constructor that produces the log message.
         */
        static void logOnce() {
            // noop by design; the only purpose of this method is to request an initialization of this class
            // and a consequent call of the static initializer
            if (IS_GRAAL_VM) {
                if (LOGGED.compareAndSet(false, true)) {
                    logLeak();
                }
            }
        }
    }
}
