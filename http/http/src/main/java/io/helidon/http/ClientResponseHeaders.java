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

package io.helidon.http;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.ParserMode;

import static io.helidon.http.HeaderNames.ACCEPT_PATCH;
import static io.helidon.http.HeaderNames.EXPIRES;
import static io.helidon.http.HeaderNames.LAST_MODIFIED;
import static io.helidon.http.HeaderNames.LOCATION;

/**
 * HTTP Headers of a client response.
 */
public interface ClientResponseHeaders extends Headers {
    /**
     * Create a new instance from headers parsed from client response.
     * Strict media type parsing mode is used for {@code Content-Type} header.
     *
     * @param responseHeaders client response headers
     * @return immutable instance of client response HTTP headers
     */
    static ClientResponseHeaders create(Headers responseHeaders) {
        return new ClientResponseHeadersImpl(responseHeaders, ParserMode.STRICT);
    }

    /**
     * Create a new instance from headers parsed from client response.
     *
     * @param responseHeaders client response headers
     * @param parserMode media type parsing mode
     * @return immutable instance of client response HTTP headers
     */
    static ClientResponseHeaders create(Headers responseHeaders, ParserMode parserMode) {
        return new ClientResponseHeadersImpl(responseHeaders, parserMode);
    }

    /**
     * Accepted patches.
     *
     * @return list of accepted patches media types
     */
    default List<HttpMediaType> acceptPatches() {
        List<String> all = all(ACCEPT_PATCH, List::of);
        List<HttpMediaType> mediaTypes = new ArrayList<>(all.size());
        for (String value : all) {
            mediaTypes.add(HttpMediaType.create(value));
        }
        return mediaTypes;
    }

    /**
     * Optionally gets the value of {@link HeaderNames#LOCATION} header.
     * <p>
     * Used in redirection, or when a new resource has been created.
     *
     * @return Location header value.
     */
    default Optional<URI> location() {
        if (contains(LOCATION)) {
            return Optional.of(get(LOCATION))
                    .map(Header::get)
                    .map(URI::create);
        }
        return Optional.empty();
    }

    /**
     * Optionally gets the value of {@link HeaderNames#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object.
     *
     * @return Last modified header value.
     */
    default Optional<ZonedDateTime> lastModified() {
        if (contains(LAST_MODIFIED)) {
            return Optional.of(get(LAST_MODIFIED))
                    .map(Header::get)
                    .map(DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Optionally gets the value of {@link HeaderNames#EXPIRES} header.
     * <p>
     * Gives the date/time after which the response is considered stale.
     *
     * @return Expires header value.
     */
    default Optional<ZonedDateTime> expires() {
        if (contains(EXPIRES)) {
            return Optional.of(get(EXPIRES))
                    .map(Header::get)
                    .map(DateTime::parse);
        }
        return Optional.empty();
    }
}
