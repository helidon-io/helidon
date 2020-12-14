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

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.BufferedEmittingPublisher;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * This publisher is always associated with a single http request. Additionally,
 * it is associated with the connection context handler and it maintains a fine
 * control of the associated context handler to perform Netty push-backing.
 */
class HttpRequestScopedPublisher extends BufferedEmittingPublisher<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(HttpRequestScopedPublisher.class.getName());

    private final ReentrantReadWriteLock.WriteLock lock = new ReentrantReadWriteLock().writeLock();
    private final ReferenceHoldingQueue<DataChunk> referenceQueue;

    HttpRequestScopedPublisher(ChannelHandlerContext ctx, ReferenceHoldingQueue<DataChunk> referenceQueue) {
        super();
        this.referenceQueue = referenceQueue;
        super.onRequest((n, demand) -> {
            if (super.isUnbounded()) {
                LOGGER.finest("Netty autoread: true");
                ctx.channel().config().setAutoRead(true);
            } else {
                LOGGER.finest("Netty autoread: false");
                ctx.channel().config().setAutoRead(false);
            }

            try {
                lock.lock();

                if (super.hasRequests()) {
                    LOGGER.finest("Requesting next chunks from Netty.");
                    ctx.channel().read();
                } else {
                    LOGGER.finest("No hook action required.");
                }
            } finally {
                lock.unlock();
            }
        });
    }

    public int emit(ByteBuf data) {
        if (isCompleted()) {
            data.release();
            return 0;
        }
        try {
            return super.emit(new ByteBufRequestChunk(data, referenceQueue));
        } finally {
            referenceQueue.release();
        }
    }

    public void clearAndRelease() {
        this.completeNow();
        super.clearBuffer(DataChunk::release);
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
    public void fail(Throwable throwable) {
        try {
            super.fail(throwable);
        } finally {
            referenceQueue.release();
        }
    }
}
