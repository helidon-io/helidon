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

package io.helidon.nima.http.encoding.deflate;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.spi.ContentEncodingProvider;

/**
 * Support for {@code deflate} content encoding.
 */
public class DeflateEncodingProvider implements ContentEncodingProvider {
    private static final Http.HeaderValue CONTENT_ENCODING_DEFLATE =
            Http.HeaderValue.createCached(Http.Header.CONTENT_ENCODING,
                                          false,
                                          false,
                                          "deflate");

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
                return new DeflaterOutputStream(new BufferedOutputStream(network, 512));
            }

            @Override
            public void headers(HeadersWritable<?> headers) {
                headers.add(CONTENT_ENCODING_DEFLATE);
                headers.remove(Http.Header.CONTENT_LENGTH);
            }
        };
    }
}
