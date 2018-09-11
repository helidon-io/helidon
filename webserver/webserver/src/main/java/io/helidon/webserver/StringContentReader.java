/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;

/**
 * The StringContentReader provides means to convert a {@link ByteBuffer} publisher to
 * a single string while using a given charset. If the charset cannot be used by the JVM,
 * the returned completion stage ends exceptionally with an {@link IllegalArgumentException}.
 */
public class StringContentReader implements Reader<String> {

    /** The default charset to use in case that no charset or no mime-type is defined in the content type header. */
    static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();

    private final Charset charset;
    private final UnsupportedCharsetException e;

    /**
     * Constructs the reader based on the charset information located in the given request.
     *
     * @param request the request to obtain the charset information from
     */
    StringContentReader(Request request) {
        this(requestContentCharset(request));
    }

    /**
     * Constructs the reader with the given charset.
     *
     * @param charset the charset to use
     */
    StringContentReader(Charset charset) {
        this.charset = charset;
        this.e = null;
    }

    /**
     * Constructs the reader with the given charset.
     *
     * @param charset the charset to use
     */
    StringContentReader(String charset) {
        Charset charsetLocal = null;
        UnsupportedCharsetException eLocal = null;
        try {
            charsetLocal = Charset.forName(charset);
        } catch (UnsupportedCharsetException e) {
            eLocal = e;
        }
        this.charset = charsetLocal;
        this.e = eLocal;
    }

    /**
     * Converts a {@link ByteBuffer} publisher to a single string while using the associated
     * charset. If the charset cannot be used by the JVM, the returned completion stage ends
     * exceptionally with an {@link IllegalArgumentException}.
     *
     * @param publisher the publisher from which to transform the byte chunks into a single
     *                  string
     * @return a completion stage representing the to be created string; if the associated
     * charset cannot be used by this JVM, it ends exceptionally with an
     * {@link IllegalArgumentException}
     */
    @Override
    public CompletionStage<String> apply(Flow.Publisher<DataChunk> publisher, Class<? super String> clazz) {
        if (charset != null) {
            return ContentReaders
                    .byteArrayReader()
                    .apply(publisher, byte[].class)
                    .thenApply(bytes -> new String(bytes, charset));
        }
        CompletableFuture result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalArgumentException(
                "Cannot produce a string with the expected charset.", e));
        return result;
    }

    /**
     * Obtain the charset from the request.
     *
     * @param request the request to extract the charset from
     * @return the charset or {@link #DEFAULT_CHARSET} if none found
     */
    static String requestContentCharset(ServerRequest request) {
        return request.headers()
                      .contentType()
                      .map(MediaType::getCharset)
                      .map(optionalCharset -> optionalCharset.orElse(DEFAULT_CHARSET))
                      .orElse(DEFAULT_CHARSET);
    }
}
