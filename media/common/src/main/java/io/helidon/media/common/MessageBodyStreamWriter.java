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

import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;

/**
 * Conversion operator that generate HTTP payload from a stream of objects.
 *
 * @param <T> type or base type supported by the operator
 */
public interface MessageBodyStreamWriter<T> extends MessageBodyOperator<MessageBodyWriterContext> {

    /**
     * Generate HTTP payload from the given stream of objects.
     *
     * @param publisher objects to convert to payload
     * @param type requested type representation
     * @param context writer context
     * @return HTTP payload publisher
     */
    Publisher<DataChunk> write(Publisher<? extends T> publisher,
                               GenericType<? extends T> type,
                               MessageBodyWriterContext context);

    /**
     * Create a marshalling function that can be used to marshall the publisher with a context.
     *
     * @param publisher objects to convert to payload
     * @return Marshalling function
     */
    default Function<MessageBodyWriterContext, Publisher<DataChunk>> marshall(Publisher<T> publisher,
                                                                              GenericType<T> type) {
        return ctx -> ctx.marshallStream(publisher, this, type);
    }
}
