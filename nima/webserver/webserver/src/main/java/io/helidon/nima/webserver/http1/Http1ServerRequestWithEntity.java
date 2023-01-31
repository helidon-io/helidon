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

package io.helidon.nima.webserver.http1;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.HttpSecurity;
import io.helidon.nima.webserver.http.ServerRequestEntity;

final class Http1ServerRequestWithEntity extends Http1ServerRequest {
    private final LazyValue<ReadableEntity> entity;
    private final ConnectionContext ctx;
    private final Http1Connection connection;
    private final boolean expectContinue;
    private final boolean continueImmediately;


    Http1ServerRequestWithEntity(ConnectionContext ctx,
                                 Http1Connection connection,
                                 Http1Config http1Config,
                                 HttpSecurity security,
                                 HttpPrologue prologue,
                                 ServerRequestHeaders headers,
                                 ContentDecoder decoder,
                                 int requestId,
                                 boolean expectContinue,
                                 CountDownLatch entityReadLatch,
                                 Supplier<BufferData> readEntityFromPipeline) {
        super(ctx, security, prologue, headers, requestId);
        this.ctx = ctx;
        this.connection = connection;
        this.expectContinue = expectContinue;
        this.continueImmediately = http1Config.continueImmediately();
        // we need the same entity instance every time the entity() method is called
        this.entity = LazyValue.create(() -> ServerRequestEntity.create(this::trySend100,
                                                                        decoder,
                                                                        it -> readEntityFromPipeline.get(),
                                                                        entityReadLatch::countDown,
                                                                        headers,
                                                                        ctx.serverContext().mediaContext()));
    }

    @Override
    public ReadableEntity content() {
        return entity.get();
    }

    @Override
    public void reset() {
        if (!continueImmediately && expectContinue) {
            connection.reset();
        }
    }

    private void trySend100(boolean drain) {
        if (!continueImmediately && expectContinue && !drain) {
            ctx.dataWriter().writeNow(BufferData.create(Http1Connection.CONTINUE_100));
        }
    }
}
