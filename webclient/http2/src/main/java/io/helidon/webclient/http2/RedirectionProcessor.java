/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;

class RedirectionProcessor {

    private RedirectionProcessor() {
    }

    static boolean redirectionStatusCode(Status status) {
        // 304 is not a redirect; it instructs a cache to reuse its stored representation.
        return status.code() != Status.NOT_MODIFIED_304.code()
                && status.family() == Status.Family.REDIRECTION;
    }

    static boolean keepsMethodAndEntity(Method method, Status status) {
        int statusCode = status.code();
        return statusCode == Status.TEMPORARY_REDIRECT_307.code()
                || statusCode == Status.PERMANENT_REDIRECT_308.code()
                || (Method.QUERY.equals(method)
                        && (statusCode == Status.MOVED_PERMANENTLY_301.code()
                        || statusCode == Status.FOUND_302.code()));
    }

    static void checkRedirectHeaders(Http2Headers headerValues) {
        if (!headerValues.httpHeaders().contains(HeaderNames.LOCATION)) {
            throw new IllegalStateException("There is no " + HeaderNames.LOCATION + " header present in the"
                                                    + " response! "
                                                    + "It is not clear where to redirect.");
        }
    }

    static void checkRedirectHeaders(ClientResponseHeaders headerValues) {
        if (!headerValues.contains(HeaderNames.LOCATION)) {
            throw new IllegalStateException("There is no " + HeaderNames.LOCATION + " header present in the"
                                                    + " response! "
                                                    + "It is not clear where to redirect.");
        }
    }

    static Http2ClientResponseImpl invokeWithFollowRedirects(Http2ClientRequestImpl request, int initial, Object entity) {
        return invokeWithFollowRedirects(request, initial, entity, false);
    }

    static Http2ClientResponseImpl invokeWithFollowRedirectsDeferringResponseCookies(Http2ClientRequestImpl request,
                                                                                     int initial,
                                                                                     Object entity) {
        return invokeWithFollowRedirects(request, initial, entity, true);
    }

    private static Http2ClientResponseImpl invokeWithFollowRedirects(Http2ClientRequestImpl request,
                                                                     int initial,
                                                                     Object entity,
                                                                     boolean deferResponseCookies) {
        return invokeWithFollowRedirects(request,
                                         initial,
                                         Http2CallEntityChain.RequestEntity.create(entity),
                                         deferResponseCookies);
    }

    static Http2ClientResponseImpl invokeWithFollowRedirects(Http2ClientRequestImpl request,
                                                             int initial,
                                                             Http2CallEntityChain.RequestEntity requestEntity) {
        return invokeWithFollowRedirects(request, initial, requestEntity, false);
    }

    static Http2ClientResponseImpl invokeWithFollowRedirects(Http2ClientRequestImpl request,
                                                             int initial,
                                                             Http2CallEntityChain.RequestEntity requestEntity,
                                                             boolean deferResponseCookies) {
        //Request object which should be used for invoking the next request. This will change in case of any redirection.
        Http2ClientRequestImpl clientRequest = request;
        int followedRedirects = initial;
        try {
            while (true) {
                if (deferResponseCookies) {
                    clientRequest.deferResponseCookies();
                }
                Http2ClientResponseImpl clientResponse = clientRequest.invokeEntity(requestEntity);
                if (!redirectionStatusCode(clientResponse.status())) {
                    return clientResponse;
                }
                Status redirectStatus = clientResponse.status();
                ClientUri sourceUri = clientResponse.lastEndpointUri();
                String redirectedUri;
                try (clientResponse) {
                    if (deferResponseCookies) {
                        ClientUri endpointUri = clientResponse.lastEndpointUri();
                        ClientUri cookieUri = clientResponse.redirectSecurityState()
                                .lastEffectiveOrigin()
                                .map(origin -> origin.apply(endpointUri))
                                .orElseGet(() -> ClientRequestOrigin.create(endpointUri).apply(endpointUri));
                        clientRequest.recordResponseCookies(cookieUri,
                                                            clientResponse.headers());
                    }
                    if (followedRedirects >= request.maxRedirects()) {
                        throw new IllegalStateException("Maximum number of request redirections ("
                                                                + request.maxRedirects() + ") reached.");
                    }
                    if (!clientResponse.headers().contains(HeaderNames.LOCATION)) {
                        throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                                + " header present in the response! "
                                                                + "It is not clear where to redirect.");
                    }
                    redirectedUri = clientResponse.headers().get(HeaderNames.LOCATION).get();
                }
                followedRedirects++;
                ClientUri redirectUri = clientRequest.resolveRedirectUri(sourceUri, redirectedUri);
                // Method and entity must be retained for 307 and 308, and for QUERY with 301 and 302.
                if (keepsMethodAndEntity(clientRequest.method(), redirectStatus)) {
                    if (!requestEntity.canStartAttempt()) {
                        throw new IllegalStateException(
                                "HTTP/2 cannot replay a one-shot request entity after redirect status "
                                        + redirectStatus.code() + ".");
                    }
                    clientRequest = new Http2ClientRequestImpl(clientRequest,
                                                               clientRequest.method(),
                                                               redirectUri,
                                                               clientRequest.properties(),
                                                               sourceUri,
                                                               requestEntity.hasEntity());
                    requestEntity.applyPreparedHeaders(clientRequest.headers());
                    clientRequest.sanitizeRedirectHeaders();
                } else {
                    //It is possible to change to GET and send no entity with all other redirect codes
                    clientRequest = new Http2ClientRequestImpl(clientRequest,
                                                               Method.GET,
                                                               redirectUri,
                                                               clientRequest.properties(),
                                                               sourceUri,
                                                               false);
                    requestEntity.restoreHeadersBeforePreparation(clientRequest.headers());
                    requestEntity.discard();
                    clientRequest.discardEntityHeaders();
                    clientRequest.sanitizeRedirectHeaders();
                }
            }
        } finally {
            requestEntity.cancelIfUnattached();
        }
    }

}
