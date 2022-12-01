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

package io.helidon.nima.http.encoding.brotli;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.Set;

import com.oracle.brotli.decoder.BrotliInputStream;
import com.oracle.brotli.encoder.BrotliOutputStream;
import io.helidon.common.Weighted;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.spi.ContentEncodingProvider;

import static io.helidon.common.http.Http.Header.CONTENT_LENGTH;

/**
 * Support for brotli content encoding.
 */
public class BrotliEncodingProvider implements ContentEncodingProvider, Weighted {
    private static final HeaderValue CONTENT_ENCODING_BROTLI = Http.Header.createCached(
            Http.Header.CONTENT_ENCODING,
            false,
            false,
            "br");

    @Override
    public Set<String> ids() {
        return Set.of("br");
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
        return network -> BrotliInputStream.builder()
                .inputStream(network)
                .build();
    }

    @Override
    public ContentEncoder encoder() {
        return new ContentEncoder() {
            @Override
            public OutputStream encode(OutputStream network) {
                return BrotliOutputStream.builder()
                        .outputStream(new BufferedOutputStream(network, 512))
                        .build();
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.add(CONTENT_ENCODING_BROTLI);
                headers.remove(CONTENT_LENGTH);
            }
        };
    }

    @Override
    public double weight() {
        return Weighted.DEFAULT_WEIGHT + 100;
    }
}
