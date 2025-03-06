/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

module io.helidon.declarative.tests.http {
    requires io.helidon.http;
    requires io.helidon.common.media.type;
    requires io.helidon.webserver;
    requires io.helidon.service.registry;
    requires jakarta.json;
    requires io.helidon.config.yaml;
    requires io.helidon.security.abac.role;
    requires io.helidon.webserver.security;
    requires io.helidon.logging.common;
    requires io.helidon.metrics.systemmeters;

    exports io.helidon.declarative.tests.http;
}