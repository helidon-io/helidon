/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webclient.blocking;

import java.net.URI;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.WebClientResponseHeaders;


class BlockingWebClientResponseImpl implements BlockingWebClientResponse {

    private final WebClientResponse res;

    private final BlockingMessageBodyReadableContent content;

    BlockingWebClientResponseImpl(WebClientResponse res) {
        this.res = res;
        content = BlockingMessageBodyReadableContent.create(res.content());
    }

    static BlockingWebClientResponse create(WebClientResponse res) {
        return new BlockingWebClientResponseImpl(res);
    }

    @Override
    public Http.ResponseStatus status() {
        return res.status();
    }

    @Override
    public BlockingMessageBodyReadableContent content() {
        return content;
    }

    @Override
    public WebClientResponseHeaders headers() {
        return res.headers();
    }

    @Override
    public Http.Version version() {
        return res.version();
    }

    @Override
    public URI lastEndpointURI() {
        return res.lastEndpointURI();
    }

    @Override
    public void close() {
        res.close().await();
    }
}
