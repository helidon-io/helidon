/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpPrologue;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.ServerRequestEntity;

final class Http1ServerRequestWithEntity extends Http1ServerRequest {
    private static final System.Logger LOGGER = System.getLogger(Http1ServerRequestWithEntity.class.getName());

    private final LazyValue<ReadableEntity> entity;
    private final ConnectionContext ctx;
    private final Http1Connection connection;
    private final boolean expectContinue;
    private final boolean continueImmediately;
    private boolean continueSent;
    private UnaryOperator<InputStream> streamFilter = UnaryOperator.identity();

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
        // if continue immediately, then continue was already sent
        // if not expecting continue, then we must expect the entity is being sent, and so we also treat it as if continue
        // was sent
        this.continueSent = continueImmediately || !expectContinue;
        // we need the same entity instance every time the entity() method is called
        this.entity = LazyValue.create(() -> ServerRequestEntity.create(this::trySend100,
                                                                        streamFilter,
                                                                        decoder,
                                                                        it -> readEntityFromPipeline.get(),
                                                                        entityReadLatch::countDown,
                                                                        headers,
                                                                        ctx.listenerContext().mediaContext()));
    }

    @Override
    public ReadableEntity content() {
        return entity.get();
    }

    @Override
    public void reset() {
        if (!continueSent) {
            connection.reset();
        }
    }

    @Override
    public boolean continueSent() {
        return continueSent;
    }

    @Override
    public void streamFilter(UnaryOperator<InputStream> filterFunction) {
        Objects.requireNonNull(filterFunction);
        UnaryOperator<InputStream> current = this.streamFilter;
        this.streamFilter = it -> filterFunction.apply(current.apply(it));
    }

    @Override
    public String toString() {
        return super.toString() + " with entity";
    }

    private void trySend100(boolean drain) {
        if (!continueImmediately && expectContinue && !drain) {
            BufferData buffer = BufferData.create(Http1Connection.CONTINUE_100);
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                ctx.log(LOGGER, System.Logger.Level.DEBUG, "send: status %s", Status.CONTINUE_100);
                ctx.log(LOGGER, System.Logger.Level.DEBUG, "send %n%s", buffer.debugDataHex());
            }
            ctx.dataWriter().writeNow(buffer);
            this.continueSent = true;
        }
    }
}
