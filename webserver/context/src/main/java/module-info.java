/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

/**
 * Helidon WebServer Context Support.
 * Integration of {@link io.helidon.common.context.Context} with {@link io.helidon.webserver.WebServer}.
 * Register {@link io.helidon.webserver.context.ContextFeature} with
 * {@link io.helidon.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)}.
 * This will create a request specific context accessible through {@link io.helidon.common.context.Contexts#context()}.
 */
module io.helidon.webserver.context {

    requires io.helidon.common.context;
    requires io.helidon.common.context.http;
    requires io.helidon.webserver;

    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.config;

    provides io.helidon.webserver.spi.ServerFeatureProvider
            with io.helidon.webserver.context.ContextFeatureProvider;

    exports io.helidon.webserver.context;

}