/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import java.util.concurrent.Flow;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;

final class CharSequenceBodyStreamWriter implements MessageBodyStreamWriter<CharSequence> {

    private static final CharSequenceBodyStreamWriter DEFAULT = new CharSequenceBodyStreamWriter();

    private CharSequenceBodyStreamWriter() {
    }

    static CharSequenceBodyStreamWriter create() {
        return DEFAULT;
    }

    @Override
    public Flow.Publisher<DataChunk> write(final Flow.Publisher<? extends CharSequence> publisher,
                                           final GenericType<? extends CharSequence> type,
                                           final MessageBodyWriterContext context) {
        context.contentType(MediaType.TEXT_PLAIN);
        return Multi.create(publisher).map(s -> DataChunk.create(true, context.charset().encode(s.toString())));
    }

    @Override
    public PredicateResult accept(final GenericType<?> type, final MessageBodyWriterContext context) {
        return PredicateResult.supports(CharSequence.class, type);
    }
}
