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

/**
 * Conversion operator that can convert HTTP payload into a stream of objects.
 *
 * @param <T> type or base type supported by the operator
 */
public interface MessageBodyStreamReader<T> extends MessageBodyOperator<MessageBodyReaderContext> {

    /**
     * Convert the given HTTP payload into objects of the given type.
     *
     * @param <U> requested type
     * @param publisher HTTP payload
     * @param type requested type representation
     * @param context reader context
     * @return publisher
     */
    <U extends T> Publisher<U> read(Publisher<DataChunk> publisher,
                                    GenericType<U> type,
                                    MessageBodyReaderContext context);

    /**
     * Unmarshall the given content using this reader.
     *
     * @param content readable content to unmarshall
     * @param type requested type
     * @return publisher
     */
    default Publisher<T> unmarshall(MessageBodyReadableContent content, GenericType<T> type) {
        return content.readerContext().unmarshallStream(content, this, type);
    }

    /**
     * Unmarshall the given content using this reader.
     *
     * @param content readable content to unmarshall
     * @param type requested type
     * @return publisher
     */
    default Publisher<T> unmarshall(MessageBodyReadableContent content, Class<T> type) {
        return content.readerContext().unmarshallStream(content, this, GenericType.create(type));
    }
}
