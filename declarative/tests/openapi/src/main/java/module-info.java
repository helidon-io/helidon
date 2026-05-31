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

@SuppressWarnings({"helidon:api:incubating", "helidon:api:preview"})
module io.helidon.declarative.tests.openapi {
    requires io.helidon.common.media.type;
    requires io.helidon.config.yaml;
    requires io.helidon.http;
    requires io.helidon.json.binding;
    requires io.helidon.json.schema;
    requires io.helidon.logging.common;
    requires io.helidon.openapi;
    requires io.helidon.service.registry;
    requires io.helidon.webserver;
    requires io.helidon.webclient.api;

    // required for generated binding
    requires io.helidon.webserver.context;

    exports io.helidon.declarative.tests.openapi;
}
