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

import java.io.OutputStream;

import io.helidon.common.GenericType;
import io.helidon.common.http.Headers;
import io.helidon.common.http.HeadersWritable;

/**
 * Writer of entity into bytes.
 *
 * @param <T> type of entity
 */
public interface EntityWriter<T> {
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
               HeadersWritable<?> responseHeaders);

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
               HeadersWritable<?> headers);
}
