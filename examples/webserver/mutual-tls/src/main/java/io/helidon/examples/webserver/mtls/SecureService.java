/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.webserver.mtls;

import io.helidon.http.HeaderValues;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;

import static io.helidon.http.HeaderNames.X_HELIDON_CN;

class SecureService implements HttpService {
    @Override
    public void routing(HttpRules rules) {
        rules.any((req, res) -> {
            // close to avoid re-using cached connections on the client side
            res.header(HeaderValues.CONNECTION_CLOSE);
            res.send("Hello " + req.headers().get(X_HELIDON_CN).get() + "!");
        });
    }
}
