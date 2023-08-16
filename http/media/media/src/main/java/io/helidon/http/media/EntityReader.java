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

import java.io.InputStream;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;

/**
 * Reader of entity into a specific type.
 *
 * @param <T> type of object to read
 */
public interface EntityReader<T> {
    /**
     * Read server request entity and close the stream.
     *
     * @param type    type of entity
     * @param stream  stream to read from
     * @param headers request headers
     * @return correctly typed entity
     */
    T read(GenericType<T> type, InputStream stream, Headers headers);

    /**
     * Read client response entity and close the stream.
     *
     * @param type            type of entity
     * @param stream          stream to read from
     * @param requestHeaders  request headers
     * @param responseHeaders response headers
     * @return correctly typed entity
     */
    T read(GenericType<T> type,
           InputStream stream,
           Headers requestHeaders,
           Headers responseHeaders);
}
