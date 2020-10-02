/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.media.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

/**
 * Accessor for the {@link Charset} specified by a content-type header.
 * @deprecated since 2.0.0, use {@link MessageBodyContext#charset()} instead
 */
@Deprecated(since = "2.0.0")
public class ContentTypeCharset {

    /**
     * Cannot be instantiated.
     */
    private ContentTypeCharset() {
    }

    /**
     * Returns the {@link Charset} specified in the content-type header, using {@link StandardCharsets#UTF_8}
     * as the default.
     *
     * @param headers The headers.
     * @return The charset.
     */
    public static Charset determineCharset(Parameters headers) {
        return determineCharset(headers, StandardCharsets.UTF_8);
    }

    /**
     * Returns the {@link Charset} specified in the content type header. If not provided or an error occurs on lookup,
     * the given default is returned.
     *
     * @param headers The headers.
     * @param defaultCharset The default.
     * @return The charset.
     */
    public static Charset determineCharset(Parameters headers, Charset defaultCharset) {
        return headers.first(Http.Header.CONTENT_TYPE)
                      .map(MediaType::parse)
                      .flatMap(MediaType::charset)
                      .map(sch -> {
                          try {
                              return Charset.forName(sch);
                          } catch (Exception e) {
                              return null;
                          }
                      })
                      .orElse(defaultCharset);
    }
}
