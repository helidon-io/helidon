/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyStreamReader;

/**
 * {@link ReadableBodyPart} stream reader.
 */
public final class BodyPartBodyStreamReader implements MessageBodyStreamReader<ReadableBodyPart> {

    /**
     * Private to enforce the use of {@link #create()}.
     */
    private BodyPartBodyStreamReader() {
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyReaderContext context) {
        return BodyPart.class.isAssignableFrom(type.rawType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends ReadableBodyPart> Publisher<U> read(Publisher<DataChunk> publisher, GenericType<U> type,
            MessageBodyReaderContext context) {

        String boundary = null;
        MediaType contentType = context.contentType().orElse(null);
        if (contentType != null) {
            boundary = contentType.parameters().get("boundary");
        }
        if (boundary == null) {
            throw new IllegalStateException("boudary header is missing");
        }
        MultiPartDecoder decoder = MultiPartDecoder.create(boundary, context);
        publisher.subscribe(decoder);
        return (Publisher<U>) decoder;
    }

    /**
     * Create a new instance of {@link BodyPartBodyStreamReader}.
     *
     * @return BodyPartBodyStreamReader
     */
    public static BodyPartBodyStreamReader create() {
        return  new BodyPartBodyStreamReader();
    }
}
