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

package io.helidon.http.encoding.gzip;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;

import static io.helidon.http.HeaderNames.CONTENT_LENGTH;

/**
 * Support for gzip content encoding.
 */
public class GzipEncoding implements ContentEncoding {
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.createCached(HeaderNames.CONTENT_ENCODING,
                                                                                  false,
                                                                                  false,
                                                                                  "gzip");

    private final String name;

    GzipEncoding(String name) {
        this.name = name;
    }

    /**
     * Create a new gzip encoding.
     *
     * @return a new gzip encoding
     */
    public static GzipEncoding create() {
        return new GzipEncoding("gzip");
    }

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
            public OutputStream apply(OutputStream network) {
                try {
                    return new GZIPOutputStream(network);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void headers(WritableHeaders<?> headers) {
                headers.add(CONTENT_ENCODING_GZIP);
                headers.remove(CONTENT_LENGTH);
            }
        };
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "gzip";
    }
}
