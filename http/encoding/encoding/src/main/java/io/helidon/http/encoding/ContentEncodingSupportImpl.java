/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.http.encoding;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import io.helidon.http.BadRequestException;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.HttpException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

class ContentEncodingSupportImpl implements ContentEncodingContext {
    private final boolean encodingEnabled;
    private final boolean decodingEnabled;
    private final Map<String, ContentEncoder> encoders;
    private final Map<String, ContentDecoder> decoders;
    private final List<String> contentEncodingIds;
    private final ContentEncodingContextConfig config;

    ContentEncodingSupportImpl(ContentEncodingContextConfig config) {
        this.config = config;

        Map<String, ContentEncoder> encoders = new LinkedHashMap<>();
        Map<String, ContentDecoder> decoders = new HashMap<>();

        for (ContentEncoding contentEncoding : config.contentEncodings()) {
            Set<String> ids = contentEncoding.ids();
            if (contentEncoding.supportsEncoding()) {
                for (String id : ids) {
                    id = id.toLowerCase(Locale.ROOT);
                    ContentEncoder encoder = contentEncoding.encoder();
                    encoders.putIfAbsent(id, encoder);
                }
            }

            if (contentEncoding.supportsDecoding()) {
                for (String id : ids) {
                    id = id.toLowerCase(Locale.ROOT);
                    decoders.putIfAbsent(id, contentEncoding.decoder());
                }
            }
        }

        this.encodingEnabled = !encoders.isEmpty();
        this.decodingEnabled = !decoders.isEmpty();
        this.contentEncodingIds = contentEncodingIds(config.contentEncodings());

        encoders.put(AcceptEncoding.IDENTITY, ContentEncoder.NO_OP);
        decoders.put(AcceptEncoding.IDENTITY, ContentDecoder.NO_OP);

        this.encoders = encoders;
        this.decoders = decoders;
    }

    static List<String> contentEncodingIds(List<ContentEncoding> contentEncodings) {
        Set<String> result = new LinkedHashSet<>();
        for (ContentEncoding contentEncoding : contentEncodings) {
            if (contentEncoding.supportsEncoding()) {
                responseCoding(contentEncoding).ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public boolean contentEncodingEnabled() {
        return encodingEnabled;
    }

    @Override
    public boolean contentDecodingEnabled() {
        return decodingEnabled;
    }

    @Override
    public boolean contentEncodingSupported(String encodingId) {
        return encoders.get(encodingId.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public List<String> contentEncodingIds() {
        return contentEncodingIds;
    }

    @Override
    public boolean contentDecodingSupported(String encodingId) {
        return decoders.get(encodingId.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
        ContentEncoder encoder = encoders.get(encodingId.toLowerCase(Locale.ROOT));
        if (encoder == null) {
            throw new NoSuchElementException("Encoding for " + encodingId + " not available");
        }
        return encoder;
    }

    @Override
    public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
        ContentDecoder decoder = decoders.get(encodingId.toLowerCase(Locale.ROOT));
        if (decoder == null) {
            throw new NoSuchElementException("Decoding for " + encodingId + " not available");
        }
        return decoder;

    }

    @Override
    public ContentEncoder encoder(Headers headers) {
        if (!headers.contains(HeaderNames.ACCEPT_ENCODING)) {
            return ContentEncoder.NO_OP;
        }

        AcceptEncoding acceptEncoding = AcceptEncoding.create(headers);
        if (!acceptEncoding.valid()) {
            throw new BadRequestException("Invalid Accept-Encoding header");
        }

        if (!contentEncodingEnabled()) {
            return ContentEncoder.NO_OP;
        }

        Map<String, EncodingCandidate> candidates = new LinkedHashMap<>();
        Set<String> probedCodings = new LinkedHashSet<>();
        for (String coding : contentEncodingIds) {
            addEncodingCandidate(candidates, probedCodings, coding);
        }
        for (AcceptEncoding.CodingQuality quality : acceptEncoding.acceptedCodings(false)) {
            String coding = quality.coding();
            if (contentEncodingSupported(coding)) {
                addEncodingCandidate(candidates, probedCodings, coding);
            }
        }

        Optional<AcceptEncoding.CodingQuality> selected = acceptEncoding.best(List.copyOf(candidates.keySet()));
        if (selected.isEmpty()) {
            throw new HttpException("No acceptable response content encoding", Status.NOT_ACCEPTABLE_406, true);
        }
        String selectedCoding = selected.get().coding();
        if (AcceptEncoding.IDENTITY.equals(selectedCoding)) {
            return ContentEncoder.NO_OP;
        }

        return candidates.get(selectedCoding).encoder();
    }

    @Override
    public ContentEncodingContextConfig prototype() {
        return config;
    }

    private static Optional<String> responseCoding(ContentEncoding contentEncoding) {
        Set<String> ids = contentEncoding.ids();
        String type = contentEncoding.type().toLowerCase(Locale.ROOT);
        for (String id : ids) {
            String normalized = id.toLowerCase(Locale.ROOT);
            if (type.equals(normalized) && !AcceptEncoding.IDENTITY.equals(normalized)) {
                return Optional.of(normalized);
            }
        }
        return ids.stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .filter(id -> !AcceptEncoding.IDENTITY.equals(id))
                .sorted()
                .findFirst();
    }

    private static String responseCoding(String coding, ContentEncoder encoder) {
        WritableHeaders<?> headers = WritableHeaders.create();
        encoder.headers(headers);
        if (headers.contains(HeaderNames.CONTENT_ENCODING)) {
            return headers.get(HeaderNames.CONTENT_ENCODING).get().toLowerCase(Locale.ROOT);
        }
        return coding;
    }

    private void addEncodingCandidate(Map<String, EncodingCandidate> candidates,
                                      Set<String> probedCodings,
                                      String coding) {
        String normalized = coding.toLowerCase(Locale.ROOT);
        if (!probedCodings.add(normalized)) {
            return;
        }
        ContentEncoder encoder = encoders.get(normalized);
        if (encoder == null) {
            return;
        }
        String emittedCoding = responseCoding(normalized, encoder);
        candidates.putIfAbsent(emittedCoding, new EncodingCandidate(encoder));
    }

    private record EncodingCandidate(ContentEncoder encoder) {
    }

}
