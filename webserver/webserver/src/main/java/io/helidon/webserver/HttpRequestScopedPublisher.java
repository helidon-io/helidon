/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.OriginThreadPublisher;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * This publisher is always associated with a single http request. Additionally,
 * it is associated with the connection context handler and it maintains a fine
 * control of the associated context handler to perform Netty push-backing.
 */
class HttpRequestScopedPublisher extends OriginThreadPublisher<DataChunk, ByteBuf> {

    private static final Logger LOGGER = Logger.getLogger(HttpRequestScopedPublisher.class.getName());

    private volatile boolean suspended = false;
    private final ChannelHandlerContext ctx;
    private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();
    private final ReferenceHoldingQueue<DataChunk> referenceQueue;

    HttpRequestScopedPublisher(ChannelHandlerContext ctx, ReferenceHoldingQueue<DataChunk> referenceQueue) {
        super();
        this.referenceQueue = referenceQueue;
        this.ctx = ctx;
    }

    @Override
    protected void hookOnCancel() {
        ctx.close();
    }

    /**
     * This method is called whenever
     * {@link java.util.concurrent.Flow.Subscription#request(long)} is
     * called on the very one associated subscription with this publisher in
     * order to trigger next channel read on the associated
     * {@link ChannelHandlerContext}.
     * <p>
     * This method can be called by any thread.
     *
     * @param n the requested count
     * @param result the current total cumulative requested count; ranges
     * between [0, {@link Long#MAX_VALUE}] where the max indicates that this
     * publisher is unbounded
     */
    @Override
    protected void hookOnRequested(long n, long result) {
        if (result == Long.MAX_VALUE) {
            LOGGER.finest("Netty autoread: true");
            ctx.channel().config().setAutoRead(true);
        } else {
            LOGGER.finest("Netty autoread: false");
            ctx.channel().config().setAutoRead(false);
        }

        try {
            lock.lock();

            if (suspended && super.tryAcquire() > 0) {
                suspended = false;

                LOGGER.finest("Requesting next chunks from Netty.");
                ctx.channel().read();
            } else {
                LOGGER.finest("No hook action required.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long tryAcquire() {
        try {
            lock.lock();
            long l = super.tryAcquire();
            if (l <= 0) {
                suspended = true;
            }
            return l;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void submit(ByteBuf data) {
        try {
            super.submit(data);
        } finally {
            referenceQueue.release();
        }
    }

    @Override
    public void complete() {
        try {
            super.complete();
        } finally {
            referenceQueue.release();
        }
    }

    @Override
    public void error(Throwable throwable) {
        try {
            super.error(throwable);
        } finally {
            referenceQueue.release();
        }
    }

    @Override
    protected DataChunk wrap(ByteBuf data) {
        return new ByteBufRequestChunk(data, referenceQueue);
    }

    @Override
    protected void drain(DataChunk item) {
        item.release();
    }
}
