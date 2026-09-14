/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http3;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientUri;

class Http3RedirectionProcessor {
    private Http3RedirectionProcessor() {
    }

    static boolean redirectionStatusCode(Status status) {
        return status.code() != Status.NOT_MODIFIED_304.code()
                && status.family() == Status.Family.REDIRECTION;
    }

    static Http3ClientResponse invokeWithFollowRedirects(Http3ClientRequestImpl request,
                                                         int initial,
                                                         Http3RequestBody requestBody) {
        Http3ClientRequestImpl clientRequest = request;
        Http3RequestBody bodyToBeSent = requestBody;
        int followedRedirects = initial;
        try {
            while (true) {
                Http3ClientResponse clientResponse = clientRequest.invokeEntity(bodyToBeSent);
                if (!redirectionStatusCode(clientResponse.status())) {
                    return clientResponse;
                }
                try (clientResponse) {
                    if (followedRedirects >= request.maxRedirects()) {
                        throw new IllegalStateException("Maximum number of request redirections ("
                                                                + request.maxRedirects() + ") reached.");
                    }
                    followedRedirects++;
                    if (!clientResponse.headers().contains(HeaderNames.LOCATION)) {
                        throw new IllegalStateException("There is no " + HeaderNames.LOCATION
                                                                + " header present in the response! "
                                                                + "It is not clear where to redirect.");
                    }
                    String redirectedUri = clientResponse.headers().get(HeaderNames.LOCATION).get();
                    ClientUri redirectSourceUri = ClientUri.create(clientResponse.lastEndpointUri());
                    ClientUri redirectUri = clientRequest.resolveRedirectUri(redirectSourceUri, redirectedUri);

                    if (keepsMethodAndEntity(clientRequest.method(), clientResponse.status())) {
                        if (!bodyToBeSent.canStartAttempt()) {
                            throw new IllegalStateException("HTTP/3 cannot replay a one-shot request body after redirect status "
                                                                    + clientResponse.status().code() + ".");
                        }
                        clientRequest = new Http3ClientRequestImpl(clientRequest,
                                                                   clientRequest.method(),
                                                                   redirectUri,
                                                                   request.properties(),
                                                                   redirectSourceUri,
                                                                   !bodyToBeSent.isEmpty(),
                                                                   bodyToBeSent,
                                                                   true);
                    } else {
                        Http3RequestBody discardedBody = bodyToBeSent;
                        bodyToBeSent = Http3RequestBody.create(BufferData.EMPTY_BYTES);
                        clientRequest = new Http3ClientRequestImpl(clientRequest,
                                                                   Method.GET,
                                                                   redirectUri,
                                                                   request.properties(),
                                                                   redirectSourceUri,
                                                                   false,
                                                                   discardedBody,
                                                                   false);
                        discardedBody.cancelIfUnattached();
                    }
                }
            }
        } finally {
            requestBody.cancelIfUnattached();
        }
    }

    private static boolean keepsMethodAndEntity(Method method, Status status) {
        int statusCode = status.code();
        return statusCode == Status.TEMPORARY_REDIRECT_307.code()
                || statusCode == Status.PERMANENT_REDIRECT_308.code()
                || (Method.QUERY.equals(method)
                        && (statusCode == Status.MOVED_PERMANENTLY_301.code()
                        || statusCode == Status.FOUND_302.code()));
    }
}
