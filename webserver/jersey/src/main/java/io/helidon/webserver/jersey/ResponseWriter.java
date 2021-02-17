/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import javax.ws.rs.core.MediaType;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.webserver.ByteBufDataChunk;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

/**
 * Implementation of Jersey's SPI to write responses. The Webserver's class
 * {@code BareResponseImpl} will subscribe to the publisher of {@code DataChunk}'s
 * created by this class. All buffers created by this class are allocated
 * from Netty's pool.
 */
class ResponseWriter implements ContainerResponseWriter {
    private static final Logger LOGGER = Logger.getLogger(ResponseWriter.class.getName());

    private final ServerResponse res;
    private final ServerRequest req;
    private final CompletableFuture<Void> whenHandleFinishes;
    private final DataChunkOutputStream publisher;

    ResponseWriter(ServerResponse res, ServerRequest req, CompletableFuture<Void> whenHandleFinishes) {
        this.res = res;
        this.req = req;
        this.whenHandleFinishes = whenHandleFinishes;
        this.publisher = new DataChunkOutputStream();
    }

    @Override
    public OutputStream writeResponseStatusAndHeaders(long contentLength, ContainerResponse context)
            throws ContainerException {
        if (context.getStatus() == 404 && contentLength == 0) {
            whenHandleFinishes.thenRun(() -> {
                LOGGER.finer("Skipping the handling and forwarding to downstream WebServer filters.");
                req.next();
            });
            return new OutputStream() {
                @Override
                public void write(int b) {
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

        publisher.autoFlush(MediaType.SERVER_SENT_EVENTS_TYPE.isCompatible(context.getMediaType()));
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

    private static class DataChunkOutputStream extends OutputStream
            implements Flow.Publisher<DataChunk>, Flow.Subscription {

        private static final int BYTEBUF_DEFAULT_SIZE = 4096;
        private static final long CANCEL = Long.MIN_VALUE;
        private static final long ERROR = CANCEL + 1;
        private static final long WAIT = -1;
        private static final ByteBuf ZERO_BUF = Unpooled.buffer(0);

        private byte[] oneByteArray;
        private ByteBuf byteBuf;
        private boolean autoFlush;
        private Flow.Subscriber<? super DataChunk> downstream;
        private Semaphore sema;
        private final AtomicLong requested = new AtomicLong();

        public void autoFlush(boolean autoFlush) {
            this.autoFlush = autoFlush;
        }

        // -- OutputStream -----------------------------------------------------

        @Override
        public void write(int b) throws IOException {
            if (oneByteArray == null) {
                oneByteArray = new byte[1];
            }
            oneByteArray[0] = (byte) b;
            write(oneByteArray, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (byteBuf == null) {
                    awaitRequest();
                    byteBuf = PooledByteBufAllocator.DEFAULT.buffer(BYTEBUF_DEFAULT_SIZE);
                }

                int rem = Math.min(byteBuf.writableBytes(), len);
                byteBuf.writeBytes(b, off, rem);
                off += rem;
                len -= rem;
                if (byteBuf.writableBytes() == 0) {
                    publish(autoFlush, byteBuf);
                    byteBuf = null;
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (byteBuf == null) {
                awaitRequest();
                publish(true, ZERO_BUF);
            } else {
                publish(true, byteBuf);
                byteBuf = null;
            }
        }

        @Override
        public void close() throws IOException {
            if (byteBuf != null) {
                flush();
            }

            long r = error();
            if (r == CANCEL || r == ERROR) {
                return;
            }

            downstream.onComplete();
        }

        // -- Flow.Publisher --------------------------------------------------

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> sub) {
            downstream = sub;
            sub.onSubscribe(this);
        }

        // -- Subscription ----------------------------------------------------

        @Override
        public void cancel() {
            long r = requested.getAndSet(CANCEL);
            if (r == WAIT) {
                sema.release();
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                long req = requested.getAndUpdate(r -> r != CANCEL ? ERROR : r);
                if (req == WAIT) {
                    sema.release();
                }
                return;
            }

            long req = requested.getAndUpdate(r -> r == WAIT ? n - 1
                    : r < 0 ? r : Long.MAX_VALUE - n > r ? r + n : Long.MAX_VALUE);
            if (req == WAIT) {
                sema.release();
            }
        }

        // -- Private methods -------------------------------------------------

        private void publish(boolean doFlush, ByteBuf buf) {
            DataChunk d = new ByteBufDataChunk(doFlush, true, buf::release, buf);
            if (requested.get() >= 0) {
                downstream.onNext(d);
            } else {
                d.release();
                error();
            }
        }

        private long error() {
            long r = requested.get();
            if (r == ERROR) {
                r = requested.getAndSet(CANCEL);
                if (r == ERROR) {
                    downstream.onError(new IllegalArgumentException("Bad request is not allowed"));
                }
            }
            return r;
        }

        private void awaitRequest() throws IOException {
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
    }
}
