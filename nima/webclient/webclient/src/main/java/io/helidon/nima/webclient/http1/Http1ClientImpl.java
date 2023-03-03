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

package io.helidon.nima.webclient.http1;

import io.helidon.common.http.Http;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.LoomClient;
import io.helidon.nima.webclient.UriHelper;

class Http1ClientImpl extends LoomClient implements Http1Client {
    private final int maxHeaderSize;
    private final int maxStatusLineLength;
    private final boolean sendExpect100Continue;
    private final boolean validateHeaders;
    private final MediaContext mediaContext;
    private final int connectionQueueSize;

    Http1ClientImpl(Http1ClientBuilder builder) {
        super(builder);
        this.maxHeaderSize = builder.maxHeaderSize();
        this.maxStatusLineLength = builder.maxStatusLineLength();
        this.sendExpect100Continue = builder.sendExpect100Continue();
        this.validateHeaders = builder.validateHeaders();
        this.mediaContext = builder.mediaContext();
        this.connectionQueueSize = builder.connectionQueueSize();
    }

    @Override
    public Http1ClientRequest method(Http.Method method) {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = (uri() == null) ? UriHelper.create() : UriHelper.create(uri(), query);

        return new ClientRequestImpl(this, method, helper, query);
    }

    int maxHeaderSize() {
        return this.maxHeaderSize;
    }

    int maxStatusLineLength() {
        return this.maxStatusLineLength;
    }

    boolean sendExpect100Continue() {
        return this.sendExpect100Continue;
    }

    boolean validateHeaders() {
        return this.validateHeaders;
    }

    MediaContext mediaContext() {
        return this.mediaContext;
    }

    int connectionQueueSize() {
        return this.connectionQueueSize;
    }
}
