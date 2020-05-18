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

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;

/**
 * Conversion operator that generate HTTP payload from objects.
 *
 * @param <T> type or base type supported by the operator
 */
public interface MessageBodyWriter<T> extends MessageBodyOperator<MessageBodyWriterContext> {

    /**
     * Generate HTTP payload from the objects of the given type.
     *
     * @param single object single publisher to convert to payload
     * @param type requested type
     * @param context the context providing the headers abstraction
     * @return Publisher of objects
     */
    Publisher<DataChunk> write(Single<? extends T> single, GenericType<? extends T> type, MessageBodyWriterContext context);
}
