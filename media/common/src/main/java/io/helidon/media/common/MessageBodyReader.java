/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
 * Conversion operator that can convert HTTP payload into one object.
 *
 * @param <T> type or base type supported by the operator
 */
public interface MessageBodyReader<T> extends MessageBodyOperator<MessageBodyReaderContext> {

    /**
     * Whether this reader processes input incrementally without materializing it in memory.
     * This includes copying input to a file and returning a lazy entity that consumes input later.
     * Streaming readers are not subject to {@code max-buffered-entity-size}; independently configured
     * payload limits still apply. The default is {@code false}, including for application-defined readers.
     * A lazy entity owns its subscription and must release its input when consumption finishes or is cancelled.
     *
     * @return whether the reader processes input without materializing it in memory
     */
    default boolean streaming() {
        return false;
    }

    /**
     * Convert a HTTP payload into a Single publisher of the given type.
     *
     * @param <U> actual requested type parameter
     * @param publisher HTTP payload
     * @param type requested type
     * @param context the context providing the headers abstraction
     * @return Single publisher
     */
    <U extends T> Single<U> read(Publisher<DataChunk> publisher, GenericType<U> type, MessageBodyReaderContext context);

    /**
     * Unmarshall the given content using this reader.
     *
     * @param content readable content to unmarshall
     * @param type requested type
     * @return Single publisher
     */
    default Single<T> unmarshall(MessageBodyReadableContent content, GenericType<T> type) {
        return (Single<T>) content.readerContext().unmarshall(content, this, type);
    }

    /**
     * Unmarshall the given content using this reader.
     *
     * @param content readable content to unmarshall
     * @param type requested type
     * @return Single publisher
     */
    default Single<T> unmarshall(MessageBodyReadableContent content, Class<T> type) {
        return (Single<T>) content.readerContext().unmarshall(content, this, GenericType.create(type));
    }
}
