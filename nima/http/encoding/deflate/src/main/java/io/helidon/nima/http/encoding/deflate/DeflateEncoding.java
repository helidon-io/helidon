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

package io.helidon.nima.http.encoding.deflate;

import java.io.OutputStream;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.http.Http;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.ContentEncoding;

/**
 * Support for {@code deflate} content encoding.
 */
public class DeflateEncoding implements ContentEncoding {
    private static final Http.HeaderValue CONTENT_ENCODING_DEFLATE =
            Http.Header.createCached(Http.Header.CONTENT_ENCODING,
                                     false,
                                     false,
                                     "deflate");
    private final String name;

    DeflateEncoding(String name) {
        this.name = name;
    }

    /**
     * Create a new deflate encoding.
     *
     * @return deflate encoding
     */
    public static DeflateEncoding create() {
        return new DeflateEncoding("deflate");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "deflate";
    }

    @Override
    public Set<String> ids() {
        return Set.of("deflate");
    }

    @Override
    public boolean supportsEncoding() {
        return true;
    }

    @Override
    public boolean supportsDecoding() {
        return true;
    }

    @Override
    public ContentDecoder decoder() {
        return InflaterInputStream::new;
    }

    @Override
    public ContentEncoder encoder() {
        return new ContentEncoder() {
            @Override
            public OutputStream encode(OutputStream network) {
                return new DeflaterOutputStream(network);
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.add(CONTENT_ENCODING_DEFLATE);
                headers.remove(Http.Header.CONTENT_LENGTH);
            }
        };
    }
}
