/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 * Helidon SE OpenAPI U/I Support.
 */
module io.helidon.integrations.openapi.ui {

    requires java.logging;
    requires transitive io.helidon.config;
    requires io.helidon.config.metadata;
    requires transitive io.helidon.openapi;
    requires transitive io.helidon.servicecommon.rest;
    requires transitive io.helidon.webserver;
    requires io.helidon.webserver.staticcontent;

    requires smallrye.open.api.ui;

    exports io.helidon.integrations.openapi.ui;
    provides io.helidon.openapi.OpenApiUiFactory with io.helidon.integrations.openapi.ui.OpenApiUiFactoryFull;
}
