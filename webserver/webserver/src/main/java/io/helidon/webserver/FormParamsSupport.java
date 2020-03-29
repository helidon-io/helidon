/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentReaders;

/**
 * Provides support for form parameters in requests, adding a reader for URL-encoded text
 * (if the request's media type so indicates) and also adding a reader for {@code FormParams}.
 * <p>
 * Developers will typically add this support to routing:
 * <pre>{@code
 * Routing.builder()
 *        .register(FormParamSupport.create())
 *        . ... // any other handlers
 * }</pre>
 * <p>
 * When responding to a request, the developer can use
 * <pre>{@code
 *     request.content().as(FormParams.class).thenApply(fp -> ...)
 * }</pre>
 * and use all the methods defined on {@link FormParams} (which extends
 * {@link io.helidon.common.http.Parameters}).
 */
public class FormParamsSupport implements Service, Handler {

    private static final FormParamsSupport INSTANCE = new FormParamsSupport();

    @Override
    public void update(Routing.Rules rules) {
        rules.any(this);
    }

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        MediaType reqMediaType = req.headers().contentType().orElse(MediaType.TEXT_PLAIN);
        Charset charset = reqMediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);

        req.content().registerReader(FormParams.class,
                (chunks, type) -> readContent(reqMediaType, charset, chunks)
                        .map(s -> FormParams.create(s, reqMediaType)).toStage());

        req.next();
    }

    /**
     *
     * @return the singleton instance of {@code FormParamSupport}
     */
    public static FormParamsSupport create() {
        return INSTANCE;
    }

    private static Single<String> readContent(MediaType mediaType, Charset charset,
            Publisher<DataChunk> chunks) {
        return mediaType.equals(MediaType.APPLICATION_FORM_URLENCODED)
                ? ContentReaders.readURLEncodedString(chunks, charset)
                : ContentReaders.readString(chunks, charset);
    }
}
