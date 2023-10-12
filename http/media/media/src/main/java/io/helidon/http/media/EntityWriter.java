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

import java.io.OutputStream;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

/**
 * Writer of entity into bytes.
 *
 * @param <T> type of entity
 */
public interface EntityWriter<T> {
    /**
     * Whether this entity writer can provide more information about each entity instance, such as content length.
     *
     * @return whether {@code instanceWriter} methods are supported;
     *         If not one of the {@code write} methods would be called instead
     */
    default boolean supportsInstanceWriter() {
        return false;
    }

    /**
     * Client request entity instance writer.
     *
     * @param type            type of entity
     * @param object          object to write
     * @param requestHeaders  request headers
     * @return instance writer ready to write the provided entity
     */
    default InstanceWriter instanceWriter(GenericType<T> type,
                                          T object,
                                          WritableHeaders<?> requestHeaders) {
        throw new UnsupportedOperationException("This writer does not support instance writers: " + getClass().getName());
    }

    /**
     * Server response entity instance writer.
     *
     * @param type            type of entity
     * @param object          object to write
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     * @return instance writer ready to write the provided entity
     */
    default InstanceWriter instanceWriter(GenericType<T> type,
                                          T object,
                                          Headers requestHeaders,
                                          WritableHeaders<?> responseHeaders) {
        throw new UnsupportedOperationException("This writer does not support instance writers: " + getClass().getName());
    }

    /**
     * Write server response entity and close the stream.
     *
     * @param type            type of entity
     * @param object          object to write
     * @param outputStream    output stream to write to
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     */
    void write(GenericType<T> type,
               T object,
               OutputStream outputStream,
               Headers requestHeaders,
               WritableHeaders<?> responseHeaders);

    /**
     * Write client request entity and close the stream.
     *
     * @param type         type of entity
     * @param object       object to write
     * @param outputStream output stream to write to
     * @param headers      request headers
     */
    void write(GenericType<T> type,
               T object,
               OutputStream outputStream,
               WritableHeaders<?> headers);
}
