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

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link Parameters} (Form parameters).
 */
class FormParamsBodyWriter implements MessageBodyWriter<Parameters> {

    private static final FormParamsBodyWriter DEFAULT = new FormParamsBodyWriter();
    private static final HttpMediaType DEFAULT_FORM_MEDIA_TYPE = HttpMediaType.create(MediaTypes.APPLICATION_FORM_URLENCODED);

    private FormParamsBodyWriter() {
    }

    static MessageBodyWriter<Parameters> create() {
        return DEFAULT;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        //User didn't have to set explicit content type. In that case set default and class filters out unsupported types.
        return context.contentType()
                .or(() -> Optional.of(DEFAULT_FORM_MEDIA_TYPE))
                .map(HttpMediaType::mediaType)
                .filter(mediaType -> mediaType == MediaTypes.APPLICATION_FORM_URLENCODED
                        || mediaType == MediaTypes.TEXT_PLAIN)
                .map(it -> PredicateResult.supports(Parameters.class, type))
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Flow.Publisher<DataChunk> write(Single<? extends Parameters> single,
                                           GenericType<? extends Parameters> type,
                                           MessageBodyWriterContext context) {
        HttpMediaType mediaType = context.contentType().orElseGet(() -> {
            context.contentType(DEFAULT_FORM_MEDIA_TYPE);
            return DEFAULT_FORM_MEDIA_TYPE;
        });
        Charset charset = mediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);

        return single.flatMap(new FormParamsToChunks(mediaType.mediaType(), charset));
    }

    static final class FormParamsToChunks implements Mapper<Parameters, Flow.Publisher<DataChunk>> {

        private final MediaType mediaType;
        private final Charset charset;

        FormParamsToChunks(MediaType mediaType, Charset charset) {
            this.mediaType = mediaType;
            this.charset = charset;
        }

        @Override
        public Flow.Publisher<DataChunk> map(Parameters formParams) {
            return ContentWriters.writeCharSequence(transform(formParams), charset);
        }

        private String transform(Parameters formParams) {
            char separator = separator();
            Function<String, String> encoder = encoder();
            StringBuilder result = new StringBuilder();
            for (String name : formParams.names()) {
                List<String> values = formParams.all(name);
                if (values.size() == 0) {
                    if (result.length() > 0) {
                        result.append(separator);
                    }
                    result.append(encoder.apply(name));
                } else {
                    for (String value : values) {
                        if (result.length() > 0) {
                            result.append(separator);
                        }
                        result.append(encoder.apply(name));
                        result.append("=");
                        result.append(encoder.apply(value));
                    }
                }
            }
            return result.toString();
        }

        private char separator() {
            if (mediaType == MediaTypes.TEXT_PLAIN) {
                return '\n';
            } else {
                return '&';
            }
        }

        private Function<String, String> encoder() {
            if (mediaType == MediaTypes.TEXT_PLAIN) {
                return (s) -> s;
            } else {
                return (s) -> URLEncoder.encode(s, charset);
            }
        }

    }
}
