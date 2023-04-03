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

package io.helidon.nima.http2.webclient;

import io.helidon.common.http.Http;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.webclient.LoomClient;
import io.helidon.nima.webclient.UriHelper;

class Http2ClientImpl extends LoomClient implements Http2Client {

    private final int maxFrameSize;
    private final long maxHeaderListSize;
    private final int initialWindowSize;
    private final int prefetch;
    private final boolean priorKnowledge;

    Http2ClientImpl(Http2ClientBuilder builder) {
        super(builder);
        this.priorKnowledge = builder.priorKnowledge();
        this.maxFrameSize = builder.maxFrameSize();
        this.maxHeaderListSize = builder.maxHeaderListSize();
        this.initialWindowSize = builder.initialWindowSize();
        this.prefetch = builder.prefetch();
    }

    @Override
    public Http2ClientRequest method(Http.Method method) {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = (uri() == null) ? UriHelper.create() : UriHelper.create(uri(), query);

        return new ClientRequestImpl(this, executor(), method, helper, tls(), query);
    }

    long maxHeaderListSize() {
        return maxHeaderListSize;
    }

    int initialWindowSize() {
        return initialWindowSize;
    }

    int prefetch() {
        return prefetch;
    }

    boolean priorKnowledge() {
        return priorKnowledge;
    }

    public int maxFrameSize() {
        return this.maxFrameSize;
    }
}
