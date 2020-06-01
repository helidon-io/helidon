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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.Single;

import static io.helidon.media.common.ByteChannelBodyWriter.DEFAULT_RETRY_SCHEMA;

/**
 * Message body writer for {@link Path}.
 */
final class PathBodyWriter implements MessageBodyWriter<Path> {

    private final static PathBodyWriter DEFAULT = new PathBodyWriter();

    /**
     * Enforces the use of {@link #create()}.
     */
    private PathBodyWriter() {
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return PredicateResult.supports(Path.class, type);
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends Path> content,
                                      GenericType<? extends Path> type,
                                      MessageBodyWriterContext context) {
        return content.flatMap(new PathToChunks(DEFAULT_RETRY_SCHEMA, context));
    }

    /**
     * Create a new of of {@link PathBodyWriter}.
     * @return new {@link Path} message body writer.
     */
    static PathBodyWriter create() {
        return DEFAULT;
    }

    /**
     * Implementation of {@link Mapper} that converts a {@link Path} to a
     * publisher of {@link DataChunk}.
     */
    private static final class PathToChunks implements Mapper<Path, Publisher<DataChunk>> {

        private final RetrySchema schema;
        private final MessageBodyWriterContext context;

        PathToChunks(RetrySchema schema, MessageBodyWriterContext context) {
            this.schema = schema;
            this.context = context;
        }

        @Override
        public Publisher<DataChunk> map(Path path) {
            try {
                context.contentType(MediaType.APPLICATION_OCTET_STREAM);
                context.contentLength(Files.size(path));
                FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
                return new ReadableByteChannelPublisher(fc, schema);
            } catch (IOException ex) {
                return Single.<DataChunk>error(ex);
            }
        }
    }
}
