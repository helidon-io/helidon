/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.config.NamedService;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Media support to be registered with {@link MediaContext}.
 */
public interface MediaSupport extends NamedService {
    /**
     * Once all providers are discovered/configured and context is established,
     * the {@link MediaContext} calls this
     * method on all providers to allow sub-resolution of readers and writers.
     *
     * @param context media context context
     */
    default void init(MediaContext context) {
    }

    /**
     * Reader for an entity.
     *
     * @param type    type of entity
     * @param headers headers belonging to this entity (such as server request headers), expected to have content type
     * @param <T>     type
     * @return reader response, whether this type is supported or not
     */
    default <T> ReaderResponse<T> reader(GenericType<T> type, Headers headers) {
        return ReaderResponse.unsupported();
    }

    /**
     * Server response writer.
     *
     * @param type            type of entity
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     * @param <T>             type
     * @return writer response, whether this type is supported or not
     */
    default <T> WriterResponse<T> writer(GenericType<T> type,
                                         Headers requestHeaders,
                                         WritableHeaders<?> responseHeaders) {
        return WriterResponse.unsupported();
    }

    /**
     * Client response reader.
     *
     * @param type            type of entity
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     * @param <T>             type
     * @return reader response, whether this type is supported or not
     */
    default <T> ReaderResponse<T> reader(GenericType<T> type,
                                         Headers requestHeaders,
                                         Headers responseHeaders) {
        return ReaderResponse.unsupported();
    }

    /**
     * Client request writer.
     *
     * @param type           type of entity
     * @param requestHeaders request headers
     * @param <T>            type
     * @return writer response, whether this type is supported or not
     */
    default <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        return WriterResponse.unsupported();
    }

    /**
     * How does this provider support the entity type.
     */
    enum SupportLevel {
        /**
         * Requested type is not supported.
         */
        NOT_SUPPORTED,
        /**
         * Requested type is compatible, but there may be a better match elsewhere (such as any POJO from JSON binding).
         */
        COMPATIBLE,
        /**
         * Requested type is supported and exactly matched (such as {@code JsonObject} from JSON processing).
         */
        SUPPORTED
    }

    /**
     * Reader response.
     *
     * @param support  how is the response supported
     * @param supplier entity reader supplier, may be null if {@link SupportLevel#NOT_SUPPORTED}
     * @param <T>      type of entity
     */
    record ReaderResponse<T>(SupportLevel support, Supplier<EntityReader<T>> supplier) {
        private static final ReaderResponse NOT_SUPPORTED = new ReaderResponse(SupportLevel.NOT_SUPPORTED, null);

        /**
         * Unsupported reader response.
         *
         * @param <T> type of entity
         * @return unsupported response (constant)
         */
        public static <T> ReaderResponse<T> unsupported() {
            return NOT_SUPPORTED;
        }
    }

    /**
     * Writer response.
     *
     * @param support  how is the response supported
     * @param supplier entity writer supplier, may be null if {@link SupportLevel#NOT_SUPPORTED}
     * @param <T>      type of entity
     */
    record WriterResponse<T>(SupportLevel support, Supplier<EntityWriter<T>> supplier) {
        private static final WriterResponse NOT_SUPPORTED = new WriterResponse(SupportLevel.NOT_SUPPORTED, null);

        /**
         * Unsupported writer response.
         *
         * @param <T> type of entity
         * @return unsupported response (constant)
         */
        public static <T> WriterResponse<T> unsupported() {
            return NOT_SUPPORTED;
        }
    }
}
