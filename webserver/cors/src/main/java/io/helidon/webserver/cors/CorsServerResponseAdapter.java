/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import io.helidon.cors.CorsResponseAdapter;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerResponse;

/**
 * Implementation of {@link CorsResponseAdapter} that adapts {@link ServerResponse}.
 * @deprecated CORS configuration is centralized to module {@code helidon-webserver-cors} with
 *         {@code io.helidon.webserver.cors.CorsFeature} either from {@link io.helidon.service.registry.ServiceRegistry}, or
 *         through one of the feature's static factory or builder methods; paths configured in config are registered first,
 *         before paths configured through service registry; this class will be removed in a future version of Helidon
 */
@Deprecated(forRemoval = true, since = "4.4.0")
class CorsServerResponseAdapter implements CorsResponseAdapter<ServerResponse> {

    private static final System.Logger LOGGER = System.getLogger(CorsServerResponseAdapter.class.getName());

    private final ServerResponse serverResponse;

    CorsServerResponseAdapter(ServerResponse serverResponse) {
        this.serverResponse = serverResponse;
    }

    @Override
    public CorsServerResponseAdapter header(HeaderName key, String value) {
        serverResponse.header(key, value);
        return this;
    }

    @Override
    public CorsServerResponseAdapter header(HeaderName key, Object value) {
        serverResponse.header(key, value.toString());
        return this;
    }

    @Override
    public ServerResponse forbidden(String message) {
        serverResponse.status(Status.create(Status.FORBIDDEN_403.code()));
        LOGGER.log(System.Logger.Level.TRACE, "Rejecting CORS request: " + message);
        return serverResponse;
    }

    @Override
    public ServerResponse ok() {
        serverResponse.status(Status.OK_200);
        return serverResponse;
    }

    @Override
    public int status() {
        return serverResponse.status().code();
    }

    @Override
    public void clearCorsHeaders() {
        var headers = serverResponse.headers();
        headers.remove(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN);
        headers.remove(HeaderNames.ACCESS_CONTROL_ALLOW_METHODS);
        headers.remove(HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS);
        headers.remove(HeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS);
        headers.remove(HeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        headers.remove(HeaderNames.ACCESS_CONTROL_MAX_AGE);

    }
}
