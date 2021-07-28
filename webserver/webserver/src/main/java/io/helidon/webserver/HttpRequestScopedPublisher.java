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
package io.helidon.webserver;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.webserver.ByteBufRequestChunk.DataChunkHoldingQueue;

import io.netty.buffer.ByteBuf;

/**
 * This publisher is always associated with a single http request. All data
 * chunks emitted by this publisher are linked to a reference queue for
 * proper cleanup.
 */
class HttpRequestScopedPublisher extends BufferedEmittingPublisher<DataChunk> {

    private final DataChunkHoldingQueue holdingQueue;

    HttpRequestScopedPublisher(DataChunkHoldingQueue holdingQueue) {
        super();
        this.holdingQueue = holdingQueue;
    }

    public void emit(ByteBuf data) {
        try {
            super.emit(new ByteBufRequestChunk(data, holdingQueue));
        } finally {
            holdingQueue.release();
        }
    }

    /**
     * Clear and release any {@link io.helidon.common.http.DataChunk DataChunk} hanging in
     * the buffer. Try self subscribe in case no one subscribed and unreleased {@link io.netty.buffer.ByteBuf ByteBufs}
     * are hanging in the netty pool.
     */
    public void clearAndRelease() {
        Multi.create(this)
                // release any chunks coming if subscription succeed
                .forEach(DataChunk::release)
                // in any case clear the buffer and release its content
                .onTerminate(() -> super.clearBuffer(DataChunk::release));
    }

    @Override
    public void complete() {
        try {
            super.complete();
        } finally {
            holdingQueue.release();
        }
    }

    @Override
    public void fail(Throwable throwable) {
        try {
            super.fail(throwable);
        } finally {
            holdingQueue.release();
        }
    }
}
