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

package io.helidon.nima.http.encoding.gzip;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.Weighted;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.spi.ContentEncodingProvider;

import static io.helidon.common.http.Http.Header.CONTENT_LENGTH;

/**
 * Support for gzip content encoding.
 */
public class GzipEncodingProvider implements ContentEncodingProvider, Weighted {
    private static final HeaderValue CONTENT_ENCODING_GZIP = HeaderValue.createCached(Http.Header.CONTENT_ENCODING,
                                                                                      false,
                                                                                      false,
                                                                                      "gzip");

    @Override
    public Set<String> ids() {
        return Set.of("gzip", "x-gzip");
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
        return network -> {
            try {
                return new GZIPInputStream(network);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    @Override
    public ContentEncoder encoder() {
        return new ContentEncoder() {
            @Override
            public OutputStream encode(OutputStream network) {
                try {
                    return new GZIPOutputStream(new BufferedOutputStream(network, 512));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void headers(HeadersWritable<?> headers) {
                headers.add(CONTENT_ENCODING_GZIP);
                headers.remove(CONTENT_LENGTH);
            }
        };
    }

    @Override
    public double weight() {
        // this has a high weight, as gzip is supported by most clients and server
        return Weighted.DEFAULT_WEIGHT + 100;
    }
}
