/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.http.restclient;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;

final class RestClientTypes {
    static final TypeName REST_CLIENT_ENDPOINT = TypeName.create("io.helidon.webclient.api.RestClient.Endpoint");
    static final TypeName REST_CLIENT_QUALIFIER = TypeName.create("io.helidon.webclient.api.RestClient.Client");
    static final TypeName REST_CLIENT_HEADER = TypeName.create("io.helidon.webclient.api.RestClient.Header");
    static final TypeName REST_CLIENT_HEADERS = TypeName.create("io.helidon.webclient.api.RestClient.Headers");
    static final TypeName REST_CLIENT_COMPUTED_HEADER = TypeName.create("io.helidon.webclient.api.RestClient.ComputedHeader");
    static final TypeName REST_CLIENT_COMPUTED_HEADERS = TypeName.create("io.helidon.webclient.api.RestClient.ComputedHeaders");
    static final TypeName REST_CLIENT_HEADER_PRODUCER = TypeName.create("io.helidon.webclient.api.RestClient.HeaderProducer");
    static final TypeName REST_CLIENT_ERROR_HANDLER = TypeName.create("io.helidon.webclient.api.RestClient.ErrorHandler");
    static final TypeName REST_CLIENT_ERROR_HANDLING = TypeName.create("io.helidon.webclient.api.RestClient.ErrorHandling");
    static final TypeName WEB_CLIENT = TypeName.create("io.helidon.webclient.api.WebClient");

    static final Annotation REST_CLIENT_QUALIFIER_INSTANCE = Annotation.create(REST_CLIENT_QUALIFIER);
    private RestClientTypes() {
    }
}
