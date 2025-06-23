/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Singleton
@Service.Named(ClientHeaderFunction.SERVICE_NAME)
class ClientHeaderFunction implements Http.HeaderFunction {
    static final String HEADER_NAME = "X-Computed";
    static final String SERVICE_NAME = "greet-client-header";

    private static final HeaderName EXPECTED = HeaderNames.create(HEADER_NAME);
    private static final Header VALUE = HeaderValues.create(EXPECTED, "Computed-Value");

    @Override
    public Optional<Header> apply(HeaderName name) {
        if (EXPECTED.equals(name)) {
            return Optional.of(VALUE);
        }
        return Optional.empty();
    }
}
