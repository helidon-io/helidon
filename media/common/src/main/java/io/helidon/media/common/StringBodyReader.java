/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Flow.Publisher;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;

/**
 * Message body reader for {@link String}.
 */
final class StringBodyReader implements MessageBodyReader<String> {

    private static final StringBodyReader DEFAULT = new StringBodyReader();

    /**
     * Private to enforce the use of {@link #create()}.
     */
    private StringBodyReader() {
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
        return PredicateResult.supports(String.class, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends String> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type,
            MessageBodyReaderContext context) {

        return (Single<U>) ContentReaders.readString(publisher, context.charset());
    }

    /**
     * Create a new instance of {@link StringBodyReader}.
     * @return {@link String} message body reader.
     */
    static StringBodyReader create() {
        return DEFAULT;
    }
}
