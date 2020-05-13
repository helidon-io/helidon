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
package io.helidon.media.multipart.common;

import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * {@link WriteableMultiPart} writer.
 */
public final class MultiPartBodyWriter implements MessageBodyWriter<WriteableMultiPart> {

    /**
     * The default boundary used for encoding multipart messages.
     */
    public static final String DEFAULT_BOUNDARY = "[^._.^]==>boundary<==[^._.^]";

    /**
     * The actual boundary string.
     */
    private final String boundary;

    /**
     * Private to enforce the use of {@link #create(java.lang.String)}.
     * @param boundary the multipart boundary delimiter
     */
    private MultiPartBodyWriter(String boundary) {
        this.boundary = boundary;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyWriterContext context) {
        return WriteableMultiPart.class.isAssignableFrom(type.rawType());
    }

    @Override
    public Publisher<DataChunk> write(Single<WriteableMultiPart> content, GenericType<? extends WriteableMultiPart> type,
            MessageBodyWriterContext context) {

        context.contentType(MediaType.MULTIPART_FORM_DATA);
        return content.flatMap(new MultiPartToChunks(boundary, context));
    }

    /**
     * Create a new instance of {@link MultiPartBodyWriter} with the specified
     * boundary delimiter.
     *
     * @param boundary boundary string
     * @return MultiPartWriter
     */
    public static MultiPartBodyWriter create(String boundary) {
        return new MultiPartBodyWriter(boundary);
    }

    /**
     * Create a new writer instance that uses the default boundary delimiter.
     *
     * @see #DEFAULT_BOUNDARY
     * @return MultiPartWriter
     */
    public static MultiPartBodyWriter create() {
        return new MultiPartBodyWriter(DEFAULT_BOUNDARY);
    }

    private static final class MultiPartToChunks implements Mapper<WriteableMultiPart, Publisher<DataChunk>> {

        private final MultiPartEncoder encoder;

        MultiPartToChunks(String boundary, MessageBodyWriterContext context) {
            this.encoder = MultiPartEncoder.create(boundary, context);
        }

        @Override
        public Publisher<DataChunk> map(WriteableMultiPart multiPart) {
            Multi.just(multiPart.bodyParts()).subscribe(encoder);
            return encoder;
        }
    }
}
