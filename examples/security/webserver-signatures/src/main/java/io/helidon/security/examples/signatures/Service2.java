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
package io.helidon.security.examples.signatures;

import java.util.Optional;

import io.helidon.common.http.HttpMediaTypes;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;

class Service2 implements HttpService {

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{*}", this::handle);
    }

    private void handle(ServerRequest req, ServerResponse res) {
        Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
        res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
        res.send("Response from service2, you are: \n" + securityContext
                .flatMap(SecurityContext::user)
                .map(Subject::toString)
                .orElse("Security context is null") + ", service: " + securityContext
                .flatMap(SecurityContext::service)
                .map(Subject::toString));
    }
}
