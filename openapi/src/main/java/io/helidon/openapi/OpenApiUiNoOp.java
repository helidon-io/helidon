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
package io.helidon.openapi;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Implementation of {@link OpenApiUi} which provides no UI support but simply honors the interface.
 */
class OpenApiUiNoOp implements OpenApiUi {

    private static final MediaType[] SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT = new MediaType[0];
    /**
     *
     * @return new builder for an {@code OpenApiUiNoOp} service
     */
    static OpenApiUiNoOp.Builder builder() {
        return new Builder();
    }

    private OpenApiUiNoOp(Builder builder) {
    }

    @Override
    public void update(Routing.Rules rules) {
    }

    @Override
    public MediaType[] supportedMediaTypes() {
        return SUPPORTED_TEXT_MEDIA_TYPES_AT_OPENAPI_ENDPOINT;
    }

    @Override
    public boolean prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response) {
        return false;
    }

    static class Builder extends OpenApiUiBase.Builder<Builder, OpenApiUiNoOp> {

        @Override
        public OpenApiUiNoOp build() {
            return new OpenApiUiNoOp(this);
        }
    }
}
