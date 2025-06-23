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
import io.helidon.http.HeaderValues;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Singleton
@Service.Named(ServerHeaderFunction.SERVICE_NAME)
class ServerHeaderFunction implements Http.HeaderFunction {
    static final String SERVICE_NAME = "greet-server-header-producer";
    static final String HEADER_NAME = "X-Computed";

    private static final Header HEADER = HeaderValues.create(HEADER_NAME, "Server-Produced");

    @Override
    public Optional<Header> apply(HeaderName name) {
        return Optional.of(HEADER);
    }
}
