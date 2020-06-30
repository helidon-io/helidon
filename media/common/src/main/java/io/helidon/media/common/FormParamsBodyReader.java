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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;

/**
 * Message body reader for {@link FormParams}.
 */
class FormParamsBodyReader implements MessageBodyReader<FormParams> {

    private static final FormParamsBodyReader DEFAULT = new FormParamsBodyReader();

    private FormParamsBodyReader() {
    }

    static FormParamsBodyReader create() {
        return DEFAULT;
    }

    @Override
    public PredicateResult accept(GenericType<?> type, MessageBodyReaderContext context) {
        return context.contentType()
                .filter(mediaType -> mediaType == MediaType.APPLICATION_FORM_URLENCODED
                        || mediaType == MediaType.TEXT_PLAIN)
                .map(it -> PredicateResult.supports(FormParams.class, type))
                .orElse(PredicateResult.NOT_SUPPORTED);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends FormParams> Single<U> read(Flow.Publisher<DataChunk> publisher,
                                                 GenericType<U> type,
                                                 MessageBodyReaderContext context) {
        MediaType mediaType = context.contentType().orElseThrow();
        Charset charset = mediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);

        Single<String> result = mediaType.equals(MediaType.APPLICATION_FORM_URLENCODED)
                ? ContentReaders.readURLEncodedString(publisher, charset)
                : ContentReaders.readString(publisher, charset);
        return (Single<U>) result.map(formStr -> FormParams.create(formStr, mediaType));
    }

}
