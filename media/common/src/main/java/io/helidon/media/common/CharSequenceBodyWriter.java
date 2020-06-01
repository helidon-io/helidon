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
 * Writer for {@code CharSequence}.
 */
final class CharSequenceBodyWriter implements MessageBodyWriter<CharSequence> {

    private static final CharSequenceBodyWriter DEFAULT = new CharSequenceBodyWriter();

    /**
     * Enforce the use of {@link #create()}.
     */
    private CharSequenceBodyWriter() {
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        return PredicateResult.supports(CharSequence.class, type);
    }

    @Override
    public Publisher<DataChunk> write(Single<? extends CharSequence> content,
                                      GenericType<? extends CharSequence> type,
                                      MessageBodyWriterContext context) {

        context.contentType(MediaType.TEXT_PLAIN);
        return content.flatMap(new CharSequenceToChunks(context.charset()));
    }

    /**
     * Return an instance of {@link CharSequenceBodyWriter}.
     *
     * @return {@link CharSequence} message body writer.
     */
    static CharSequenceBodyWriter create() {
        return DEFAULT;
    }

    private static final class CharSequenceToChunks implements Mapper<CharSequence, Publisher<DataChunk>> {

        private final Charset charset;

        CharSequenceToChunks(Charset charset) {
            this.charset = charset;
        }

        @Override
        public Publisher<DataChunk> map(CharSequence content) {
            return ContentWriters.writeCharSequence(content, charset);
        }
    }
}
