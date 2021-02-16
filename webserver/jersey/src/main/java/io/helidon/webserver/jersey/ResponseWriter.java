/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import io.helidon.webserver.ByteBufDataChunk;
import io.netty.buffer.ByteBuf;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.netty.buffer.Unpooled;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

/**
 * The ResponseWriter.
 */
class ResponseWriter implements ContainerResponseWriter {
    private static final Logger LOGGER = Logger.getLogger(ResponseWriter.class.getName());

    private final DataChunkOutputStream<ByteBuf> publisher;

    private final ServerResponse res;
    private final ServerRequest req;
    private final CompletableFuture<Void> whenHandleFinishes;

    ResponseWriter(ServerResponse res, ServerRequest req, CompletableFuture<Void> whenHandleFinishes) {
        this.res = res;
        this.req = req;
        this.whenHandleFinishes = whenHandleFinishes;
        this.publisher = new DataChunkOutputStream<>(new SharedBuffersArena());
    }

    @Override
    public OutputStream writeResponseStatusAndHeaders(long contentLength, ContainerResponse context)
            throws ContainerException {

        //
        // TODO also check that nothing was written an nothing was read
        //
        if (context.getStatus() == 404 && contentLength == 0) {
            whenHandleFinishes.thenRun(() -> {
                LOGGER.finer("Skipping the handling and forwarding to downstream WebServer filters.");

                req.next();
            });
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // noop
                }
            };
        }

        res.status(Http.ResponseStatus.create(context.getStatus(), context.getStatusInfo().getReasonPhrase()));

        if (contentLength >= 0) {
            res.headers().put(Http.Header.CONTENT_LENGTH, String.valueOf(contentLength));
        }

        for (Map.Entry<String, List<String>> entry : context.getStringHeaders().entrySet()) {
            res.headers().put(entry.getKey(), entry.getValue());
        }

        publisher.setAutoflush(MediaType.SERVER_SENT_EVENTS_TYPE.isCompatible(context.getMediaType()));
        res.send(publisher);

        return publisher;
    }

    @Override
    public boolean suspend(long timeOut, TimeUnit timeUnit, TimeoutHandler timeoutHandler) {
        if (timeOut != 0) {
            throw new UnsupportedOperationException("Currently, time limited suspension is not supported!");
        }
        return true;
    }

    @Override
    public void setSuspendTimeout(long timeOut, TimeUnit timeUnit) throws IllegalStateException {
        throw new UnsupportedOperationException("Currently, extending the suspension time is not supported!");
    }

    @Override
    public void commit() {
        try {
            // Jersey doesn't close the OutputStream when there is no entity
            // as such the publisher needs to be closed from here ...
            // it is assumed it's possible to close the publisher, the OutputStream, multiple times
            publisher.close();
        } catch (IOException e) {
            // based on implementation of 'close', this never happens
            throw new IllegalStateException("Unexpected IO Exception received!", e);
        }
    }

    @Override
    public void failure(Throwable error) {
        LOGGER.finer(() -> "Jersey handling finished with an exception; message: " + error.getMessage());

        req.next(error);
    }

    @Override
    public boolean enableResponseBuffering() {
        // Jersey should not try to do the buffering
        return false;
    }

    private static class DataChunkOutputStream<T> extends OutputStream implements Flow.Publisher<DataChunk>, Flow.Subscription {
        
        private byte[] ONE_BYTE;
        private static final ByteBuf ZERO_BUF = Unpooled.buffer(0);
        private ByteBuf buf;
        private T tok;
        private boolean autoflush;
        private Arena<T> arena;

        private Flow.Subscriber downstream;
        private Semaphore sema;
        private AtomicLong requested = new AtomicLong();
        private static final long CANCEL = Long.MIN_VALUE;
        private static final long ERROR = CANCEL + 1;
        private static final long WAIT = -1;

        public DataChunkOutputStream(Arena<T> arena) {
            this.arena = arena;
        }

        public void setAutoflush(boolean autoflush) {
            this.autoflush = autoflush;
        }

        public void write(int b) throws IOException {
            if (ONE_BYTE == null) {
                ONE_BYTE = new byte[1];
            }
            ONE_BYTE[0] = (byte) b;
            write(ONE_BYTE, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (buf == null) {
                    awaitRequest(); // await for request
                    buf = alloc();  // and then for a buffer
                }

                int rem = Math.min(buf.writableBytes(), len);
                buf.writeBytes(b, off, rem);
                off += rem;
                len -= rem;
                if (buf.writableBytes() == 0) {
                    buf = null;
                    publish(autoflush, tok);
                    tok = null;
                }
            }
        }

        public void flush() throws IOException {
            if (buf == null) {
                awaitRequest();
                publish(true, null);
            } else {
                buf = null;
                publish(true, tok);
                tok = null;
            }
        }

        public void close() throws IOException {
            if (buf != null) {
                flush();
            }

            long r = error();
            if (r == CANCEL || r == ERROR) {
                return;
            }

            downstream.onComplete();
        }

        protected void publish(boolean doFlush, T tok) {
            DataChunk d = tok == null ? new ByteBufDataChunk(doFlush, true, ZERO_BUF)
                    : arena.dataChunk(doFlush, tok);

            if (requested.get() >= 0) {
                downstream.onNext(d);
            } else {
                d.release();
                error();
            }
        }

        protected long error() {
            long r = requested.get();
            if (r == ERROR) {
                r = requested.getAndSet(CANCEL);
                if (r == ERROR) {
                    downstream.onError(new IllegalArgumentException("Bad request is not allowed"));
                }
            }

            return r;
        }

        protected ByteBuf alloc() {
            tok = arena.alloc();
            return arena.byteBuffer(tok);
        }

        protected void awaitRequest() throws IOException {
            if (requested.get() == 0 && sema == null) {
                sema = new Semaphore(0);
            }
            long req = requested.getAndUpdate(r -> r + 1 > 0 ? r - 1 : r);
            if (req == 0) {
                sema.acquireUninterruptibly();
                req = requested.get();
            }

            if (req == ERROR) {
                error();
                req = CANCEL;
            }

            if (req == CANCEL) {
                throw new IOException("Bad news: the stream has been closed");
            }
        }

        public void subscribe(Flow.Subscriber<? super DataChunk> sub) {
            downstream = sub;
            sub.onSubscribe(this);
        }

        public void cancel() {
            long r = requested.getAndSet(CANCEL);

            if (r == WAIT) {
                sema.release();
            }
        }

        public void request(long n) {
            if (n <= 0) {
                long req = requested.getAndUpdate(r -> r != CANCEL ? ERROR : r);
                if (req == WAIT) {
                    sema.release();
                }
                return;
            }

            long req = requested.getAndUpdate(r -> r == WAIT ? n - 1 :
                    r < 0 ? r :
                            Long.MAX_VALUE - n > r ? r + n : Long.MAX_VALUE
            );

            if (req == WAIT) {
                sema.release();
            }
        }
    }

    public interface Arena<T> {
        /**
         * Returns arena-specific token T. May block, if arena has a size bound.
         */
        T alloc();

        /**
         * Returns writeable ByteBuf for the arena-specific token T
         */
        ByteBuf byteBuffer(T tok);

        /**
         * Constructs a read-only DataChunk for the arena-specific token T, whose
         * ByteBuf has been filled, and ready to be read from.
         */
        DataChunk dataChunk(boolean doFlush, T tok);
    }

    public static class SharedBuffersArena implements Arena<ByteBuf> {

        public DataChunk dataChunk(boolean doFlush, ByteBuf b) {
            return new ByteBufDataChunk(doFlush, true, b::release, b);
        }

        public ByteBuf alloc() {
            return Unpooled.buffer(4096);
        }

        public ByteBuf byteBuffer(ByteBuf b) {
            return b;
        }
    }
}
