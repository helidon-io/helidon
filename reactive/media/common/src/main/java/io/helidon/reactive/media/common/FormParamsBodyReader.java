/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.media.common;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.reactive.Single;

/**
 * Message body reader for {@link Parameters} (Form parameters).
 */
class FormParamsBodyReader implements MessageBodyReader<Parameters> {

    private static final FormParamsBodyReader DEFAULT = new FormParamsBodyReader();

    private static final Map<MediaType, Pattern> PATTERNS = Map.of(
            MediaTypes.APPLICATION_FORM_URLENCODED, preparePattern("&"),
            MediaTypes.TEXT_PLAIN, preparePattern("\n"));

    private FormParamsBodyReader() {
    }

    static FormParamsBodyReader create() {
        return DEFAULT;
    }

    private static Pattern preparePattern(String assignmentSeparator) {
        return Pattern.compile(String.format("([^=%1$s]+)=?([^%1$s]+)?%1$s?", assignmentSeparator));
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
        return context.contentType()
                .map(HttpMediaType::mediaType)
                .filter(mediaType -> mediaType == MediaTypes.APPLICATION_FORM_URLENCODED
                        || mediaType == MediaTypes.TEXT_PLAIN)
                .map(it -> PredicateResult.supports(Parameters.class, type))
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends Parameters> Single<U> read(Flow.Publisher<DataChunk> publisher,
                                                 GenericType<U> type,
                                                 MessageBodyReaderContext context) {
        HttpMediaType mediaType = context.contentType().orElseThrow();
        Charset charset = mediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);
        Function<String, String> decoder = decoder(mediaType.mediaType(), charset);

        return (Single<U>) ContentReaders.readString(publisher, charset)
                .map(formStr -> create(formStr, mediaType, decoder));
    }

    private Parameters create(String paramAssignments, HttpMediaType mediaType, Function<String, String> decoder) {
        Parameters.Builder builder = Parameters.builder("form-params");
        Matcher m = PATTERNS.get(mediaType.mediaType()).matcher(paramAssignments);
        while (m.find()) {
            final String key = m.group(1);
            final String value = m.group(2);
            if (value == null) {
                builder.add(decoder.apply(key));
            } else {
                builder.add(decoder.apply(key), decoder.apply(value));
            }
        }
        return builder.build();
    }

    private Function<String, String> decoder(MediaType mediaType, Charset charset) {
        if (mediaType == MediaTypes.TEXT_PLAIN) {
            return (s) -> s;
        } else {
            return (s) -> URLDecoder.decode(s, charset);
        }
    }

}
