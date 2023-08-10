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

package io.helidon.http.media.multipart;

import java.util.Optional;

import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.media.ReadableEntity;

/**
 * A single part of a multipart message.
 */
public interface ReadablePart extends ReadableEntity {
    /**
     * Name of this multipart part.
     *
     * @return param name
     */
    String name();

    /**
     * File name of this part if defined in part {@code Content-Disposition}.
     *
     * @return file name if defined
     */
    Optional<String> fileName();

    /**
     * Content type of this multipart part.
     *
     * @return HTTP content type
     */
    HttpMediaType contentType();

    /**
     * Headers of this part.
     *
     * @return headers
     */
    Headers partHeaders();
}
