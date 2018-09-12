/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extends {@link Parameters} interface by adding methods convenient for HTTP headers.
 */
public interface Headers extends Parameters {

    /**
     * Returns an unmodifiable {@link List} of all header fields - each element represents a value of a single header field
     * in the request. Consider to use {@link #value(String)} or {@link #values(String)} method instead.
     * <p>
     * Always returns a List, which may be empty if the parameter is not present.
     *
     * @param headerName the header name
     * @return a {@code List} of values with zero or greater size
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #value(String)
     * @see #values(String)
     */
    @Override
    List<String> all(String headerName);

    /**
     * Returns a header value as a single {@link String} potentially concatenated using comma character
     * from {@link #all(String) all} header fields.
     * <p>
     * Accordingly to <a href="https://tools.ietf.org/html/rfc2616#section-4.2">RFC2616, Message Headers</a>:
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
     * @see #all(String)
     * @see #values(String)
     */
    default Optional<String> value(String headerName) {
        List<String> hdrs = all(headerName);
        if (hdrs.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(hdrs.stream().collect(Collectors.joining(",")));
        }
    }

    /**
     * Returns an unmodifiable {@link List} of all comma separated header value parts - <b>Such segmentation is NOT valid for
     * all header semantics, however it is very common</b>. Refer to actual header semantics standard/description before use.
     * <p>
     * Result is composed from all header fields with requested {@code headerName} where each header value is tokenized by
     * a comma character. Tokenization respects value quoting by <i>double-quote</i> character.
     * <p>
     * Always returns a List, which may be empty if the parameter is not present.
     *
     * @param headerName the header name
     * @return a {@code List} of values with zero or greater size, never {@code null}
     * @throws NullPointerException if {@code headerName} is {@code null}
     * @see #all(String)
     * @see #value(String)
     */
    default List<String> values(String headerName) {
        return all(headerName).stream()
                .flatMap(val -> Utils.tokenize(',', "\"", true, val).stream())
                .collect(Collectors.toList());
    }
}
