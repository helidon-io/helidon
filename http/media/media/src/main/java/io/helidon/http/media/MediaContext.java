/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Media context to obtain readers and writers of various supported content types.
 */
@RuntimeType.PrototypedBy(MediaContextConfig.class)
public interface MediaContext extends RuntimeType.Api<MediaContextConfig> {
    /**
     * Create a new media context from {@link java.util.ServiceLoader}.
     *
     * @return media context
     */
    static MediaContext create() {
        return builder().build();
    }

    /**
     * Create a new media context and apply provided configuration.
     *
     * @param config configuration to use
     * @return media context
     */
    static MediaContext create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create media context from its prototype.
     *
     * @param config media context configuration
     * @return a new media context
     */
    static MediaContext create(MediaContextConfig config) {
        return new MediaContextImpl(config);
    }

    /**
     * Create media context, customizing its configuration.
     *
     * @param consumer consumer of media context builder
     * @return a new media context
     */
    static MediaContext create(Consumer<MediaContextConfig.Builder> consumer) {
        var builder = MediaContextConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * Builder to set up this media support context.
     *
     * @return a new builder
     */
    static MediaContextConfig.Builder builder() {
        return MediaContextConfig.builder();
    }

    /**
     * Reader for entity.
     *
     * @param type    type to read into (such as Pojo, JsonObject)
     * @param headers headers related to this entity
     * @param <T>     type
     * @return entity reader for the type, or a reader that will fail if none found
     */
    <T> EntityReader<T> reader(GenericType<T> type, Headers headers);

    /**
     * Writer for server response entity.
     *
     * @param type            type to write
     * @param requestHeaders  request headers, containing accepted types
     * @param responseHeaders response headers to be updated with content type
     * @param <T>             type
     * @return entity writer for the type, or a writer that will fail if none found
     */
    <T> EntityWriter<T> writer(GenericType<T> type,
                               Headers requestHeaders,
                               WritableHeaders<?> responseHeaders);

    /**
     * Reader for client response entity.
     *
     * @param type            type to read into
     * @param requestHeaders  request headers containing accepted types
     * @param responseHeaders response headers containing content type
     * @param <T>             type
     * @return entity reader for the type, or a reader that will fail if none found
     */
    <T> EntityReader<T> reader(GenericType<T> type,
                               Headers requestHeaders,
                               Headers responseHeaders);

    /**
     * Writer for client request entity.
     *
     * @param type           type to write
     * @param requestHeaders request headers to write content type to
     * @param <T>            type
     * @return entity writer for the type, or a writer that will fail if none found
     */
    <T> EntityWriter<T> writer(GenericType<T> type,
                               WritableHeaders<?> requestHeaders);
}
