/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.security.examples.webserver.digest;

import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

/**
 * Web client service that supports digest authentication.
 * Temporary until https://github.com/helidon-io/helidon/issues/7207 is fixed.
 */
class WebClientAuthenticationService implements WebClientService {

    /**
     * Property name for username.
     */
    static final String HTTP_AUTHENTICATION_USERNAME = "helidon.config.client.http.auth.username";

    /**
     * Property name for password.
     */
    static final String HTTP_AUTHENTICATION_PASSWORD = "helidon.config.client.http.auth.password";

    private final DigestAuthenticator digestAuth = new DigestAuthenticator();

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        WebClientServiceResponse response = chain.proceed(request);
        if (response.status() != Http.Status.UNAUTHORIZED_401) {
            return response;
        }
        Map<String, String> properties = request.properties();
        String username = properties.get(HTTP_AUTHENTICATION_USERNAME);
        String password = properties.get(HTTP_AUTHENTICATION_PASSWORD);
        if (username == null || password == null) {
            return response;
        }
        String challenge = response.headers().first(Http.Header.WWW_AUTHENTICATE).orElse(null);
        if (challenge == null) {
            return response;
        }
        String uri = request.uri().path().path();
        String method = request.method().text();
        String atz = digestAuth.authorization(challenge, uri, method, username, password);
        if (atz == null) {
            return response;
        }
        request.headers().add(Http.Header.AUTHORIZATION, atz);
        return chain.proceed(request);
    }
}
