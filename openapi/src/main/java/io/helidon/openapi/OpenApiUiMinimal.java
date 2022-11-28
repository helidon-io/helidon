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
 * Implementation of {@link OpenApiUi} which provides minimal U/I support.
 * <p>
 *     This class responds with HTML or plain text (according to the request's {@code Accept} header)
 *     conveying the YAML or JSON expression of the OpenAPI document (according to the {@code format}
 *     query parameter.
 * </p>
 */
class OpenApiUiMinimal extends OpenApiUiBase {

    /**
     *
     * @return new builder for an {@code OpenApiUiMinimal} service
     */
    static OpenApiUiMinimal.Builder builder() {
        return new Builder();
    }

    private static final MediaType[] SUPPORTED_TEXT_MEDIA_TYPES = new MediaType[] {
            MediaType.TEXT_HTML,
            MediaType.TEXT_PLAIN
    };

    private OpenApiUiMinimal(Builder builder) {
        super(builder, builder.documentPreparer(), builder.openApiSupportWebContext());
    }

    @Override
    public boolean prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response) {
        // The minimal implementation does not honor HTML at the main endpoint to keep the same browser behavior users saw
        // before the U/I enhancement.
        return isEnabled()
                && request.headers()
                        .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES)
                        .filter(mt -> !mt.test(MediaType.TEXT_HTML))
                        .map(mt -> sendText(request, response, mt))
                        .orElse(false);
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(webContext() + "[/]", this::sendText);
    }

    @Override
    protected MediaType[] staticTextMediaTypes() {
        return SUPPORTED_TEXT_MEDIA_TYPES;
    }

    static class Builder extends OpenApiUiBase.Builder<Builder, OpenApiUiMinimal> {

        @Override
        public OpenApiUiMinimal build() {
            return new OpenApiUiMinimal(this);
        }
    }
}
