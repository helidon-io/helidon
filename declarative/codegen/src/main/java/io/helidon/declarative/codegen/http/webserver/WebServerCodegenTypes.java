/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.http.webserver;

import io.helidon.common.types.TypeName;

final class WebServerCodegenTypes {
    static final TypeName REST_SERVER_STATUS = TypeName.create("io.helidon.webserver.http.RestServer.Status");
    static final TypeName REST_SERVER_ENDPOINT = TypeName.create("io.helidon.webserver.http.RestServer.Endpoint");
    static final TypeName REST_SERVER_LISTENER = TypeName.create("io.helidon.webserver.http.RestServer.Listener");
    static final TypeName REST_SERVER_HEADER = TypeName.create("io.helidon.webserver.http.RestServer.Header");
    static final TypeName REST_SERVER_HEADERS = TypeName.create("io.helidon.webserver.http.RestServer.Headers");
    static final TypeName REST_SERVER_COMPUTED_HEADER = TypeName.create("io.helidon.webserver.http.RestServer.ComputedHeader");
    static final TypeName REST_SERVER_COMPUTED_HEADERS = TypeName.create("io.helidon.webserver.http.RestServer.ComputedHeaders");

    static final TypeName SERVER_REQUEST = TypeName.create("io.helidon.webserver.http.ServerRequest");
    static final TypeName SERVER_RESPONSE = TypeName.create("io.helidon.webserver.http.ServerResponse");
    static final TypeName SERVER_HTTP_ROUTE = TypeName.create("io.helidon.webserver.http.HttpRoute");
    static final TypeName SERVER_HTTP_HANDLER = TypeName.create("io.helidon.webserver.http.Handler");
    static final TypeName SERVER_HTTP_FEATURE = TypeName.create("io.helidon.webserver.http.HttpFeature");
    static final TypeName SERVER_HTTP_ROUTING_BUILDER = TypeName.create("io.helidon.webserver.http.HttpRouting.Builder");
    static final TypeName SERVER_HTTP_RULES = TypeName.create("io.helidon.webserver.http.HttpRules");

    static final TypeName DECLARATIVE_ENTRY_POINTS = TypeName.create("io.helidon.webserver.http.HttpEntryPoint.EntryPoints");

    private WebServerCodegenTypes() {
    }
}
