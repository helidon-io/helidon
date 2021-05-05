/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;

/**
 * Represents a {@link Flow.Publisher publisher} of specific media type.
 */
public interface MediaPublisher extends Flow.Publisher<DataChunk> {

    /**
     * Returns a media type of published data.
     *
     * @return a published media type or {@code null} for undefined.
     */
    MediaType mediaType();

    /**
     * Creates new instance.
     *
     * @param publishedType a published media type.
     * @param publisher a publisher.
     * @return new instance.
     */
    static MediaPublisher create(MediaType publishedType, Flow.Publisher<DataChunk> publisher) {
        return new MediaPublisher() {
            @Override
            public MediaType mediaType() {
                return publishedType;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
                publisher.subscribe(subscriber);
            }
        };
    }

    /**
     * Creates a publisher of single {@link String string}.
     *
     * @param publishedType a type. If contains charset then it is used, otherwise use {@code UTF-8}. If {@code null} then
     *                     {@code text/plain} is used as a default.
     * @param charSequence A sequence to publish.
     * @return new publisher.
     */
    static MediaPublisher create(MediaType publishedType, CharSequence charSequence) {
        ByteBuffer data = Optional.ofNullable(publishedType)
                .flatMap(MediaType::charset)
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8)
                .encode(charSequence.toString());
        Flow.Publisher<DataChunk> publisher = Multi.singleton(DataChunk.create(data));
        return new MediaPublisher() {
            @Override
            public MediaType mediaType() {
                return publishedType;
            }

            @Override
            public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
                publisher.subscribe(subscriber);
            }
        };
    }
}
