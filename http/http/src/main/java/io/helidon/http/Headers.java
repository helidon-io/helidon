/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaType;

/**
 * View of HTTP Headers.
 * This API is designed to support both HTTP/1 and HTTP/2.
 * Note that HTTP/2 has all headers lower case (mandatory), while HTTP/1 headers are compared ignoring
 * case.
 * When you configure headers to be sent using HTTP/2, all names will be lowercase.
 * When you configure headers to be sent using HTTP/1, names will be sent as configured.
 * When you receive headers, the stored values (as can be obtained by {@link Header#name()})
 * will be as sent on the transport. These value will be available using any cased names (though performance may be worse
 * if uppercase letters are used to obtain HTTP/2 headers).
 */
public interface Headers extends Iterable<Header> {
    /**
     * Get all values of a header.
     *
     * @param name            name of the header
     * @param defaultSupplier supplier to obtain default values if the header is not present
     * @return list of header values
     */
    List<String> all(HeaderName name, Supplier<List<String>> defaultSupplier);

    /**
     * Whether these headers contain a header with the provided name.
     *
     * @param name header name
     * @return {@code true} if the header is defined
     */
    boolean contains(HeaderName name);

    /**
     * Whether these headers contain a header with the provided name and value.
     *
     * @param value value of the header
     * @return {@code true} if the header is defined
     */
    boolean contains(Header value);

    /**
     * Get a header value.
     *
     * @param name name of the header
     * @return value if present
     * @throws java.util.NoSuchElementException in case the header is not present
     */
    Header get(HeaderName name);

    /**
     * Returns a header value as a single {@link String} potentially concatenated using comma character
     * from {@link #all(HeaderName, java.util.function.Supplier)} header fields.
     * <p>
     * According to <a href="https://tools.ietf.org/html/rfc2616#section-4.2">RFC2616, Message Headers</a>:
     * <blockquote>
     * Multiple message-header fields with the same field-name MAY be
     * present in a message if and only if the entire field-value for that
     * header field is defined as a comma-separated list [i.e., #(values)].
     * It MUST be possible to combine the multiple header fields into one
     * "field-name: field-value" pair, without changing the semantics of the
     * message, by appending each subsequent field-value to the first, each
     * separated by a comma.
     * </blockquote>
     *
     * @param headerName the header name
     * @return all header values concatenated using comma separator
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #all(HeaderName, java.util.function.Supplier)
     * @see #values(HeaderName)
     */
    default Optional<String> value(HeaderName headerName) {
        if (contains(headerName)) {
            List<String> hdrs = all(headerName, List::of);
            return Optional.of(String.join(",", hdrs));
        }
        return Optional.empty();
    }

    /**
     * Returns a first header value.
     *
     * @param headerName the header name
     * @return the first value
     * @throws NullPointerException if {@code headerName} is {@code null}
     */
    default Optional<String> first(HeaderName headerName) {
        if (contains(headerName)) {
            return Optional.of(get(headerName).get());
        }
        return Optional.empty();
    }

    /**
     * Returns an unmodifiable {@link java.util.List} of all comma separated header value parts - <b>Such segmentation is NOT
     * valid for
     * all header semantics, however it is very common</b>. Refer to actual header semantics standard/description before use.
     * <p>
     * Result is composed from all header fields with requested {@code headerName} where each header value is tokenized by
     * a comma character. Tokenization respects value quoting by <i>double-quote</i> character.
     * <p>
     * Always returns a List, which may be empty if the header is not present.
     *
     * @param headerName the header name
     * @return a {@code List} of values with zero or greater size, never {@code null}
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #all(HeaderName, java.util.function.Supplier)
     * @see #value(HeaderName)
     */
    default List<String> values(HeaderName headerName) {
        return all(headerName, List::of)
                .stream()
                .flatMap(val -> Utils.tokenize(',', "\"", true, val).stream())
                .collect(Collectors.toList());
    }

    /**
     * Content length if defined.
     *
     * @return content length or empty if not defined
     * @see HeaderNames#CONTENT_LENGTH
     */
    default OptionalLong contentLength() {
        if (contains(HeaderNameEnum.CONTENT_LENGTH)) {
            return OptionalLong.of(get(HeaderNameEnum.CONTENT_LENGTH).get(long.class));
        }
        return OptionalLong.empty();
    }

    /**
     * Content type (if defined).
     *
     * @return content type, empty if content type is not present
     * @see HeaderNames#CONTENT_TYPE
     */
    default Optional<HttpMediaType> contentType() {
        if (contains(HeaderNameEnum.CONTENT_TYPE)) {
            return Optional.of(HttpMediaType.create(get(HeaderNameEnum.CONTENT_TYPE)
                                                            .get()));
        }
        return Optional.empty();
    }

    /**
     * Number of headers in these headers.
     *
     * @return size of these headers
     */
    int size();

    /**
     * Returns a list of acceptedTypes ({@link HeaderNames#ACCEPT} header) content discoveryTypes in
     * quality factor order. Never {@code null}.
     * Returns an empty list by default.
     *
     * @return A list of acceptedTypes media discoveryTypes.
     */
    List<HttpMediaType> acceptedTypes();

    /**
     * Whether this media type is accepted by these headers.
     * As this method is useful only for server request headers, it returns {@code true } by default.
     *
     * @param mediaTypes media type to test
     * @return {@code true} if this media type would be accepted
     */
    default boolean isAccepted(MediaType... mediaTypes) {
        return true;
    }

    /**
     * Creates a multivalued map from these headers.
     * This is extremely inefficient and should not be used.
     *
     * @return map of headers
     * @deprecated use other methods to handle headers, preferably using pull approach
     */
    @Deprecated
    default Map<String, List<String>> toMap() {
        Map<String, List<String>> headers = new HashMap<>();

        forEach(it -> headers.put(it.name(), it.allValues()));

        return headers;
    }

    /**
     * A sequential stream with these headers as the source.
     *
     * @return stream of header values
     */
    default Stream<Header> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
