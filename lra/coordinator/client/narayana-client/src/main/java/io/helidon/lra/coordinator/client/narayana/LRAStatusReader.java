/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.lra.coordinator.client.narayana;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

import org.eclipse.microprofile.lra.annotation.LRAStatus;

class LRAStatusReader implements MessageBodyReader<LRAStatus> {
    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
        return PredicateResult.supports(LRAStatus.class, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends LRAStatus> Single<U> read(Flow.Publisher<DataChunk> pub,
                                                GenericType<U> type,
                                                MessageBodyReaderContext context) {
        return (Single<U>) Multi.create(pub)
                .map(DataChunk::data)
                .flatMapIterable(List::of)
                .collectStream(Collectors.reducing(ByteBuffer.allocate(0), (ident, val) ->
                        ByteBuffer.allocate(val.capacity() + ident.capacity())
                                .put(ident)
                                .put(val.asReadOnlyBuffer())
                                .rewind()))
                .map(ByteBuffer::array)
                .map(String::new)
                .map(LRAStatus::valueOf);
    }
}
