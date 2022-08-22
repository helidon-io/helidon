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

package io.helidon.nima.http.encoding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.nima.http.encoding.spi.ContentEncodingProvider;

class ContentEncodingSupportImpl implements ContentEncodingContext {
    private static final String IDENTITY_ENCODING = "identity";

    // todo now all static, should be in builder + instance, so we can configure per server
    private static final boolean ENCODING_ENABLED;
    private static final boolean DECODING_ENABLED;

    private static final Map<String, ContentEncoder> ENCODERS;
    private static final Map<String, ContentDecoder> DECODERS;
    private static final ContentEncoder FIRST_ENCODER;

    static {
        List<ContentEncodingProvider> providers =
                HelidonServiceLoader.create(ServiceLoader.load(ContentEncodingProvider.class))
                        .asList();
        Map<String, ContentEncoder> encoders = new HashMap<>();
        Map<String, ContentDecoder> decoders = new HashMap<>();
        ContentEncoder firstEncoder = null;
        for (ContentEncodingProvider provider : providers) {
            Set<String> ids = provider.ids();
            if (provider.supportsDecoding()) {
                for (String id : ids) {
                    decoders.putIfAbsent(id, provider.decoder());
                }
            }
            if (provider.supportsEncoding()) {
                for (String id : ids) {
                    ContentEncoder encoder = provider.encoder();
                    if (firstEncoder == null) {
                        firstEncoder = encoder;
                    }
                    encoders.putIfAbsent(id, encoder);
                }
            }
        }
        encoders.put(IDENTITY_ENCODING, ContentEncoder.NO_OP);
        decoders.put(IDENTITY_ENCODING, ContentDecoder.NO_OP);

        FIRST_ENCODER = firstEncoder;

        ENCODING_ENABLED = !encoders.isEmpty();
        DECODING_ENABLED = !decoders.isEmpty();

        ENCODERS = Map.copyOf(encoders);
        DECODERS = Map.copyOf(decoders);
    }

    @Override
    public boolean contentEncodingEnabled() {
        return ENCODING_ENABLED;
    }

    @Override
    public boolean contentDecodingEnabled() {
        return DECODING_ENABLED;
    }

    @Override
    public boolean contentEncodingSupported(String encodingId) {
        return ENCODERS.get(encodingId) != null;
    }

    @Override
    public boolean contentDecodingSupported(String encodingId) {
        return DECODERS.get(encodingId) != null;
    }

    @Override
    public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
        ContentEncoder encoder = ENCODERS.get(encodingId);
        if (encoder == null) {
            throw new NoSuchElementException("Encoding for " + encodingId + " not available");
        }
        return encoder;
    }

    @Override
    public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
        ContentDecoder decoder = DECODERS.get(encodingId);
        if (decoder == null) {
            throw new NoSuchElementException("Decoding for " + encodingId + " not available");
        }
        return decoder;

    }

    @Override
    public ContentEncoder encoder(Headers headers) {
        if (!contentEncodingEnabled() || !headers.contains(Http.Header.ACCEPT_ENCODING)) {
            return ContentEncoder.NO_OP;
        }

        String acceptEncoding = headers.get(Http.Header.ACCEPT_ENCODING).value();
        /*
            Accept-Encoding: gzip
            Accept-Encoding: gzip, compress, br
            Accept-Encoding: br;q=1.0, gzip;q=0.8, *;q=0.1
         */
        String[] values = acceptEncoding.split(", ");
        List<EncodingWithQ> supported = new ArrayList<>(values.length);
        for (String value : values) {
            supported.add(EncodingWithQ.parse(value));
        }
        Collections.sort(supported);
        for (EncodingWithQ encodingWithQ : supported) {
            if ("*".equals(encodingWithQ.encoding)) {
                return FIRST_ENCODER;
            }
            if (contentEncodingSupported(encodingWithQ.encoding)) {
                return ENCODERS.get(encodingWithQ.encoding);
            }
        }

        return ContentEncoder.NO_OP;
    }

    private static class EncodingWithQ implements Comparable<EncodingWithQ> {
        private final String encoding;
        private final double q;

        EncodingWithQ(String encoding, double q) {
            this.encoding = encoding;
            this.q = q;
        }

        static EncodingWithQ parse(String value) {
            if (value.indexOf(';') != -1) {
                int index = value.indexOf(';');
                String encoding = value.substring(0, index);
                String qString = value.substring(index + 1); // q=0.1
                index = qString.indexOf('=');
                if (index == -1) {
                    throw new IllegalArgumentException("Invalid q value for Accept-Encoding");
                }
                double q = Double.parseDouble(qString.substring(index + 1));
                return new EncodingWithQ(encoding, q);
            } else {
                return new EncodingWithQ(value.trim(), 1);
            }
        }

        @Override
        public int compareTo(EncodingWithQ o) {
            return Double.compare(o.q, this.q);
        }

        @Override
        public int hashCode() {
            return Objects.hash(encoding, q);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EncodingWithQ that = (EncodingWithQ) o;
            return Double.compare(that.q, q) == 0 && encoding.equals(that.encoding);
        }

        @Override
        public String toString() {
            return encoding + ";q=" + q;
        }
    }
}
