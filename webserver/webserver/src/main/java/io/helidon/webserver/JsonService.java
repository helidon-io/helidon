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

import java.util.List;

import io.helidon.common.http.MediaType;

/**
 * A {@link Service} and abstract {@link Handler} that provides support for JSON content.
 *
 * @see Routing.Builder
 * @see Routing.Rules
 */
public abstract class JsonService implements Service, Handler {

    /**
     * Registers this handler for any HTTP method.
     *
     * @param routingRules a routing configuration where JSON support should be registered
     * @see Routing
     */
    @Override
    public void update(final Routing.Rules routingRules) {
        routingRules.any(this);
    }

    /**
     * Determines if JSON is an accepted response type, using {@code Accept} and response {@code Content-Type} headers.
     * <p>
     * Sets the response {@code Content-Type} header if not set and JSON is accepted.
     *
     * @param request a server request
     * @param response a server response
     * @return {@code true} if JSON is accepted.
     */
    protected boolean acceptsJson(ServerRequest request, ServerResponse response) {
        final MediaType responseType = response.headers().contentType().orElse(null);
        if (responseType == null) {
            // No response type set yet. See if one of the accepted types is JSON.
            final MediaType jsonResponseType = toJsonResponseType(request.headers().acceptedTypes());
            if (jsonResponseType == null) {
                // Nope
                return false;
            } else {
                // Yes, so set it as the response content-type
                response.headers().contentType(jsonResponseType);
                return true;
            }
        } else {
            return MediaType.JSON_PREDICATE.test(responseType);
        }
    }

    private MediaType toJsonResponseType(List<MediaType> acceptedTypes) {
        if (acceptedTypes == null || acceptedTypes.isEmpty()) {
            // None provided, so go ahead and return JSON.
            return MediaType.APPLICATION_JSON;
        } else {
            for (final MediaType type : acceptedTypes) {
                final MediaType responseType = toJsonResponseType(type);
                if (responseType != null) {
                    return responseType;
                }
            }
            return null;
        }
    }

    /**
     * Returns the response type for the given type if it is an accepted JSON type.
     *
     * @param acceptedType The accepted type.
     * @return The response type or {@code null} if not an accepted JSON type.
     */
    protected MediaType toJsonResponseType(MediaType acceptedType) {
        if (acceptedType.test(MediaType.APPLICATION_JSON)) {
            return MediaType.APPLICATION_JSON;
        } else if (acceptedType.hasSuffix("json")) {
            return MediaType.create(acceptedType.type(), acceptedType.subtype());
        } else {
            return null;
        }
    }
}
