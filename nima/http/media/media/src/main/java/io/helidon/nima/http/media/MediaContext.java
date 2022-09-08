/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.WritableHeaders;

/**
 * Media context to obtain readers and writers of various supported content types.
 */
public interface MediaContext {
    /**
     * Create a new media context from {@link java.util.ServiceLoader}.
     *
     * @return media context
     */
    static MediaContext create() {
        return new MediaContextImpl();
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
