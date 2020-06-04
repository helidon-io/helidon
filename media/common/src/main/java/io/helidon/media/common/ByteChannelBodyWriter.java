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
package io.helidon.media.common;

import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link ReadableByteChannel}.
 */
final class ByteChannelBodyWriter implements MessageBodyWriter<ReadableByteChannel> {

    static final RetrySchema DEFAULT_RETRY_SCHEMA = RetrySchema.linear(0, 10, 250);

    private static final ByteChannelBodyWriter DEFAULT = new ByteChannelBodyWriter(DEFAULT_RETRY_SCHEMA);

    private final ByteChannelToChunks mapper;

    /**
     * Enforce the use of the static factory method.
     *
     * @param schema retry schema
     */
    private ByteChannelBodyWriter(RetrySchema schema) {
        this.mapper = new ByteChannelToChunks(schema);
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return PredicateResult.supports(ReadableByteChannel.class, type);
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends ReadableByteChannel> content,
                                      GenericType<? extends ReadableByteChannel> type,
                                      MessageBodyWriterContext context) {

        context.contentType(MediaType.APPLICATION_OCTET_STREAM);
        return content.flatMap(mapper);
    }

    /**
     * Create a new instance of {@link ByteChannelBodyWriter} with the given {@link RetrySchema}.
     * @param schema retry schema
     * @return {@link ReadableByteChannel} message body writer
     */
    static ByteChannelBodyWriter create(RetrySchema schema) {
        return new ByteChannelBodyWriter(schema);
    }

    /**
     * Return an instance of {@link ByteChannelBodyWriter}.
     *
     * @return {@link ReadableByteChannel} message body writer
     */
    static ByteChannelBodyWriter create() {
        return DEFAULT;
    }

    /**
     * Implementation of {@link Mapper} that converts a
     * {@link ReadableByteChannel} to a publisher of {@link DataChunk}.
     */
    private static final class ByteChannelToChunks implements Mapper<ReadableByteChannel, Publisher<DataChunk>> {

        private final RetrySchema schema;

        ByteChannelToChunks(RetrySchema schema) {
            this.schema = schema;
        }

        @Override
        public Publisher<DataChunk> map(ReadableByteChannel channel) {
            return new ReadableByteChannelPublisher(channel, schema);
        }
    }
}
