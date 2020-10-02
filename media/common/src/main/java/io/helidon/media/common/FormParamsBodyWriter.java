/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Single;

/**
 * Message body writer for {@link FormParams}.
 */
class FormParamsBodyWriter implements MessageBodyWriter<FormParams> {

    private static final FormParamsBodyWriter DEFAULT = new FormParamsBodyWriter();
    private static final MediaType DEFAULT_FORM_MEDIA_TYPE = MediaType.APPLICATION_FORM_URLENCODED;

    private FormParamsBodyWriter() {
    }

    static MessageBodyWriter<FormParams> create() {
        return DEFAULT;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
        //User didn't have to set explicit content type. In that case set default and class filters out unsupported types.
        return context.contentType()
                .or(() -> Optional.of(DEFAULT_FORM_MEDIA_TYPE))
                .filter(mediaType -> mediaType == MediaType.APPLICATION_FORM_URLENCODED
                        || mediaType == MediaType.TEXT_PLAIN)
                .map(it -> PredicateResult.supports(FormParams.class, type))
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    public Flow.Publisher<DataChunk> write(Single<? extends FormParams> single,
                                           GenericType<? extends FormParams> type,
                                           MessageBodyWriterContext context) {
        MediaType mediaType = context.contentType().orElseGet(() -> {
            context.contentType(DEFAULT_FORM_MEDIA_TYPE);
            return DEFAULT_FORM_MEDIA_TYPE;
        });
        Charset charset = mediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);

        return single.flatMap(new FormParamsToChunks(mediaType, charset));
    }

    static final class FormParamsToChunks implements Mapper<FormParams, Flow.Publisher<DataChunk>> {

        private final MediaType mediaType;
        private final Charset charset;

        FormParamsToChunks(MediaType mediaType, Charset charset) {
            this.mediaType = mediaType;
            this.charset = charset;
        }

        @Override
        public Flow.Publisher<DataChunk> map(FormParams formParams) {
            return ContentWriters.writeCharSequence(transform(formParams), charset);
        }

        private String transform(FormParams formParams) {
            char separator = separator();
            Function<String, String> encoder = encoder();
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : formParams.toMap().entrySet()) {
                List<String> values = entry.getValue();
                if (values.size() == 0) {
                    if (result.length() > 0) {
                        result.append(separator);
                    }
                    result.append(encoder.apply(entry.getKey()));
                } else {
                    for (String value : values) {
                        if (result.length() > 0) {
                            result.append(separator);
                        }
                        result.append(encoder.apply(entry.getKey()));
                        result.append("=");
                        result.append(encoder.apply(value));
                    }
                }
            }
            return result.toString();
        }

        private char separator() {
            if (mediaType == MediaType.TEXT_PLAIN) {
                return '\n';
            } else {
                return '&';
            }
        }

        private Function<String, String> encoder() {
            if (mediaType == MediaType.TEXT_PLAIN) {
                return (s) -> s;
            } else {
                return (s) -> URLEncoder.encode(s, charset);
            }
        }

    }
}
