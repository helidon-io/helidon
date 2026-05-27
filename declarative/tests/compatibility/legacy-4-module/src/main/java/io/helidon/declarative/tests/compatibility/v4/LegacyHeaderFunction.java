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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.Optional;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderValues;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;

@SuppressWarnings("deprecation")
@Service.Singleton
@Service.Named(LegacyHeaderFunction.SERVICE_NAME)
public class LegacyHeaderFunction implements Http.HeaderFunction {
    static final String SERVICE_NAME = "legacy-4-header-function";
    static final String HEADER_NAME = "X-Legacy-Computed";

    @Override
    public Optional<Header> apply(HeaderName name) {
        return Optional.of(HeaderValues.create(HEADER_NAME, "legacy-computed"));
    }
}
