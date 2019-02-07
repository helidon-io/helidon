/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.media.jsonb.common;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.function.Function;

import javax.json.bind.Jsonb;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.ContentWriters;
import io.helidon.common.reactive.Flow;

public final class JsonBinding {

    public static Reader<Object> reader(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return (publisher, cls) -> ContentReaders.inputStreamReader()
            .apply(publisher)
            .thenApply(inputStream -> jsonb.fromJson(inputStream, cls));
    }

    public static Function<Object, Flow.Publisher<DataChunk>> writer(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return payload -> {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            jsonb.toJson(payload, baos);
            return ContentWriters.byteArrayWriter(false)
                .apply(baos.toByteArray());
        };
    }
  
}
