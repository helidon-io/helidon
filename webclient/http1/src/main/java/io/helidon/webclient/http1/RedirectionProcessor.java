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

package io.helidon.webclient.http1;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;

class RedirectionProcessor {

    private RedirectionProcessor() {
    }

    static boolean redirectionStatusCode(Status status) {
        // 304 is not an actual redirect - it is telling the client to use a cached value, which is outside of scope
        // of Helidon WebClient, the user must understand such a response, as they had to send an ETag
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

    static IllegalStateException maxRedirectsReached(int maxRedirects) {
        return new IllegalStateException("Maximum number of request redirections ("
                                                 + maxRedirects + ") reached.");
    }

    static Http1ClientResponseImpl invokeWithFollowRedirects(Http1ClientRequestImpl request, byte[] entity) {
        return invokeWithFollowRedirects(request, 0, entity);
    }

    static Http1ClientResponseImpl invokeWithFollowRedirects(Http1ClientRequestImpl request, int initial, byte[] entity) {
        return invokeWithFollowRedirects(request, initial, entity, false);
    }

    static Http1ClientResponseImpl invokeWithFollowRedirectsDeferringResponseCookies(Http1ClientRequestImpl request,
                                                                                     int initial,
                                                                                     byte[] entity) {
        return invokeWithFollowRedirects(request, initial, entity, true);
    }

    private static Http1ClientResponseImpl invokeWithFollowRedirects(Http1ClientRequestImpl request,
                                                                     int initial,
                                                                     byte[] entity,
                                                                     boolean deferResponseCookies) {
        //Request object which should be used for invoking the next request. This will change in case of any redirection.
        Http1ClientRequestImpl clientRequest = request;
        //Entity to be sent with the request. Will be changed when redirect happens to prevent entity sending.
        byte[] entityToBeSent = entity;
        int followedRedirects = initial;
        while (true) {
            if (deferResponseCookies) {
                clientRequest.deferResponseCookies();
            }
            Http1ClientResponseImpl clientResponse = clientRequest.invokeRequestWithEntity(entityToBeSent);
            if (!redirectionStatusCode(clientResponse.status())) {
                request.redirectSecurityState(clientRequest.redirectSecurityState());
                return clientResponse;
            }
            try (clientResponse) {
                if (deferResponseCookies) {
                    ClientUri endpointUri = clientResponse.lastEndpointUri();
                    ClientUri cookieUri = clientRequest.redirectSecurityState()
                            .lastEffectiveOrigin()
                            .map(origin -> origin.apply(endpointUri))
                            .orElseGet(() -> ClientRequestOrigin.create(endpointUri).apply(endpointUri));
                    clientRequest.recordResponseCookies(cookieUri,
                                                        clientResponse.headers());
                }
                if (followedRedirects >= request.maxRedirects()) {
                    throw maxRedirectsReached(request.maxRedirects());
                }
                followedRedirects++;
                if (!clientResponse.headers().contains(HeaderNames.LOCATION)) {
                    throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                            + " header present in the response! "
                                                            + "It is not clear where to redirect.");
                }
                String redirectedUri = clientResponse.headers().get(HeaderNames.LOCATION).get();
                ClientUri sourceUri = clientResponse.lastEndpointUri();
                ClientUri redirectUri = clientRequest.resolveRedirectUri(sourceUri, redirectedUri);
                // Method and entity must be retained for 307 and 308, and for QUERY with 301 and 302.
                if (keepsMethodAndEntity(clientRequest.method(), clientResponse.status())) {
                    clientRequest = new Http1ClientRequestImpl(clientRequest,
                                                               clientRequest.method(),
                                                               redirectUri,
                                                               clientRequest.properties(),
                                                               sourceUri,
                                                               true,
                                                               entityToBeSent.length > 0);
                } else {
                    //It is possible to change to GET and send no entity with all other redirect codes
                    entityToBeSent = BufferData.EMPTY_BYTES; //We do not want to send entity after this redirect
                    clientRequest = new Http1ClientRequestImpl(clientRequest,
                                                               Method.GET,
                                                               redirectUri,
                                                               clientRequest.properties(),
                                                               sourceUri,
                                                               false,
                                                               false);
                }
            }
        }
    }

}
