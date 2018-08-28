/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import io.netty.channel.ChannelHandlerContext;

/**
 * This publisher is always associated with a single http request. Additionally,
 * it is associated with the connection context handler and it maintains
 * a fine control of the associated context handler to perform Netty push-backing.
 */
class HttpRequestScopedPublisher extends OriginThreadPublisher {

    private static final Logger LOGGER = Logger.getLogger(HttpRequestScopedPublisher.class.getName());

    private volatile boolean suspended = false;
    private final ChannelHandlerContext ctx;
    private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();

    HttpRequestScopedPublisher(ChannelHandlerContext ctx, ReferenceHoldingQueue<ByteBufRequestChunk> referenceQueue) {
        super(referenceQueue);
        this.ctx = ctx;
    }

    @Override
    void hookOnCancel() {
        ctx.close();
    }

    /**
     * This method is called whenever {@link io.helidon.common.reactive.Flow.Subscription#request(long)}
     * is called on the very one associated subscription with this publisher in order to trigger next
     * channel read on the associated {@link ChannelHandlerContext}.
     * <p>
     * This method can be called by any thread.
     *
     * @param n      the requested count
     * @param result the current total cumulative requested count; ranges between [0, {@link Long#MAX_VALUE}]
     *               where the max indicates that this publisher is unbounded
     */
    @Override
    void hookOnRequested(long n, long result) {
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

    /**
     * In a non-blocking manner, try to acquire an allowance to publish next item.
     * If nothing is acquired, this publisher becomes suspended.
     *
     * @return original number of requests on the very one associated subscriber's subscription;
     * if {@code 0} is returned, the requester didn't obtain a permit to publish
     * next item. In case a {@link Long#MAX_VALUE} is returned,
     * the requester is informed that unlimited number of items can be published.
     */
    long tryAcquire() {
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
}
