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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link Throwable}.
 */
class ThrowableBodyWriter implements MessageBodyWriter<Throwable> {

    private static final ThrowableBodyWriter DEFAULT_TRUE = new ThrowableBodyWriter(true);
    private static final ThrowableBodyWriter DEFAULT_FALSE = new ThrowableBodyWriter(false);

    private final boolean includeStackTraces;

    private ThrowableBodyWriter() {
        this(false);
    }

    protected ThrowableBodyWriter(boolean includeStackTraces) {
        this.includeStackTraces = includeStackTraces;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return PredicateResult.supports(Throwable.class, type);
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends Throwable> content,
                                      GenericType<? extends Throwable> type,
                                      MessageBodyWriterContext context) {
        context.contentType(MediaType.TEXT_PLAIN);
        if (includeStackTraces) {
            return content.flatMap(new ThrowableToChunks(context.charset()));
        } else {
            return ContentWriters.writeCharSequence("Unexpected exception occurred.", StandardCharsets.UTF_8);
        }
    }

    /**
     * Return an instance of {@link ThrowableBodyWriter}.
     *
     * @param includeStackTraces whether stack traces are to be written
     * @return a new {@link ThrowableBodyWriter}; never {@code null}
     */
    static ThrowableBodyWriter create(boolean includeStackTraces) {
        return includeStackTraces ? DEFAULT_TRUE : DEFAULT_FALSE;
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
