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

import java.nio.charset.Charset;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link Throwable}.
 */
public class ThrowableBodyWriter implements MessageBodyWriter<Throwable> {

    private final boolean writeStackTrace;

    private ThrowableBodyWriter() {
        this(false);
    }

    protected ThrowableBodyWriter(boolean writeStackTrace) {
        this.writeStackTrace = writeStackTrace;
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyWriterContext context) {
        return Throwable.class.isAssignableFrom(type.rawType());
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends Throwable> content,
                                      GenericType<? extends Throwable> type,
                                      MessageBodyWriterContext context) {
        context.contentType(MediaType.TEXT_PLAIN);
        return content.flatMap(new ThrowableToChunks(context.charset()));
    }

    /**
     * Creates a new {@link ThrowableBodyWriter}.
     * @return a new {@link ThrowableBodyWriter}; never {@code null}
     * @see #create(boolean)
     */
    public static ThrowableBodyWriter create() {
        return create(false);
    }

    /**
     * Creates a new {@link ThrowableBodyWriter}.
     * @param writeStackTrace whether stack traces are to be written
     * @return a new {@link ThrowableBodyWriter}; never {@code null}
     */
    public static ThrowableBodyWriter create(boolean writeStackTrace) {
        return new ThrowableBodyWriter(writeStackTrace);
    }

    private static final class ThrowableToChunks implements Mapper<Throwable, Publisher<DataChunk>> {

        private final Charset charset;

        private ThrowableToChunks(Charset charset) {
            this.charset = charset;
        }

        @Override
        public Publisher<DataChunk> map(Throwable throwable) {
          return ContentWriters.writeStackTrace(throwable, charset);
        }
    }

}
