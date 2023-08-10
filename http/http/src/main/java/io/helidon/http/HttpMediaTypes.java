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

package io.helidon.http;

import java.util.function.Predicate;

import io.helidon.common.media.type.MediaTypes;

/**
 * Constants for {@link io.helidon.http.HttpMediaType}.
 */
public final class HttpMediaTypes {
    /**
     * application/json media type with UTF-8 charset.
     */
    public static final HttpMediaType JSON_UTF_8 = HttpMediaType.builder()
            .mediaType(MediaTypes.APPLICATION_JSON)
            .charset("UTF-8")
            .build();
    /**
     * Predicate to test if {@link io.helidon.common.media.type.MediaType} is {@code application/json} or has {@code json} suffix.
     */
    public static final Predicate<HttpMediaType> JSON_PREDICATE = JSON_UTF_8
            .or(mt -> mt.hasSuffix("json"));
    /**
     * text/plain media type with UTF-8 charset.
     */
    public static final HttpMediaType PLAINTEXT_UTF_8 = HttpMediaType.builder()
            .mediaType(MediaTypes.TEXT_PLAIN)
            .charset("UTF-8")
            .build();
    /**
     * Predicate to test if {@link io.helidon.common.media.type.MediaType} is {@code text/event-stream} without any parameter or with parameter "element-type".
     * This "element-type" has to be equal to "application/json".
     */
    public static final Predicate<HttpMediaType> JSON_EVENT_STREAM_PREDICATE = HttpMediaType.create(MediaTypes.TEXT_EVENT_STREAM)
            .and(mt -> mt.hasSuffix("event-stream"))
            .and(mt -> !mt.parameters().containsKey("element-type")
                    || "application/json".equals(mt.parameters().get("element-type")));

    private HttpMediaTypes() {
    }


}
