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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
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

    private static final Logger LOGGER = Logger.getLogger(OpenApiUiMinimal.class.getName());

    private static final String HTML_PREFIX = """
            <!doctype html>
            <html lang="en-US">
                <head>
                    <meta charset="utf-8"/>
                    <title>OpenAPI Document</title>
                </head>
                <body>
                    <pre>
            """;

    private static final String HTML_SUFFIX = """
                    </pre>
                </body>
            </html>
            """;

    private static final MediaType[] SUPPORTED_TEXT_MEDIA_TYPES = new MediaType[] {
            MediaType.TEXT_HTML,
            MediaType.TEXT_PLAIN
    };


    private final Map<MediaType, String> preparedDocuments = new HashMap<>();

    private OpenApiUiMinimal(Builder builder, Function<MediaType, String> documentPreparer, String openAPISupportWebContext) {
        super(builder, documentPreparer, openAPISupportWebContext);
    }

    /**
     *
     * @return new builder for an {@code OpenApiUiMinimal} service
     */
    static OpenApiUi.Builder builder() {
        return new Builder();
    }

    @Override
    public void prepareTextResponseFromMainEndpoint(ServerRequest request, ServerResponse response) {
        // The minimal implementation does not honor HTML at the main endpoint to keep the same browser behavior users saw
        // before the U/I enhancement.
        if (!isEnabled()) {
            request.next();
        } else {
            request.headers()
                    .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES)
                    .filter(mt -> !mt.test(MediaType.TEXT_HTML))
                    .ifPresentOrElse(mt -> sendText(request, response, mt),
                                     request::next);
        }
    }

    private void sendText(ServerRequest request, ServerResponse response, MediaType mediaType) {
        try {
            response
                    .addHeader(Http.Header.CONTENT_TYPE, mediaType.toString())
                    .send(prepareDocument(request.queryParams(), mediaType));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error formatting OpenAPI output as " + mediaType, e);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                    .send("Error formatting OpenAPI output. See server log.");
        }
    }

    private void sendText(ServerRequest request, ServerResponse response) {
        if (!isEnabled()) {
            request.next();
        } else {
            request.headers()
                    .bestAccepted(SUPPORTED_TEXT_MEDIA_TYPES)
                    .ifPresentOrElse(mt -> sendText(request, response, mt),
                                     request::next);
        }
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(webContent(), this::sendText);
    }

    private String prepareDocument(Parameters queryParameters, MediaType mediaType) throws IOException {
        String result = null;
        if (preparedDocuments.containsKey(mediaType)) {
            return preparedDocuments.get(mediaType);
        }
        MediaType resultMediaType = queryParameters
                .first(OpenAPISupport.OPENAPI_ENDPOINT_FORMAT_QUERY_PARAMETER)
                .map(OpenAPISupport.QueryParameterRequestedFormat::chooseFormat)
                .map(OpenAPISupport.QueryParameterRequestedFormat::mediaType)
                .orElse(OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE);

        result = prepareDocument(resultMediaType);
        if (mediaType.test(MediaType.TEXT_HTML)) {
            result = embedInHtml(result);
        }
        preparedDocuments.put(resultMediaType, result);
        return result;
    }

    private String embedInHtml(String text) {
        return HTML_PREFIX + text + HTML_SUFFIX;
    }

    static class Builder extends OpenApiUiBase.Builder {

        @Override
        public OpenApiUi build(Function<MediaType, String> documentPreparer, String openAPIWebContext) {
            return new OpenApiUiMinimal(this, documentPreparer, openAPIWebContext);
        }
    }
}
