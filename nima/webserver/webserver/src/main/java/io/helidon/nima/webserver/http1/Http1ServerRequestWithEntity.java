/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

    Http1ServerRequestWithEntity(ConnectionContext ctx,
                                 HttpSecurity security, HttpPrologue prologue,
                                 ServerRequestHeaders headers,
                                 ContentDecoder decoder,
                                 int requestId,
                                 CountDownLatch entityReadLatch,
                                 Supplier<BufferData> readEntityFromPipeline) {
        super(ctx, security, prologue, headers, requestId);
        // we need the same entity instance every time the entity() method is called
        this.entity = LazyValue.create(() -> ServerRequestEntity.create(decoder,
                                                                        it -> readEntityFromPipeline.get(),
                                                                        entityReadLatch::countDown,
                                                                        headers,
                                                                        ctx.serverContext().mediaContext()));
    }

    @Override
    public ReadableEntity content() {
        return entity.get();

    }
}
