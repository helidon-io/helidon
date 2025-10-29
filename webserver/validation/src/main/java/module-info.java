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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Add error handler for validation to WebServer HTTP routing.
 */
@SuppressWarnings("removal")
@Features.Name("Validation")
@Features.Path({"Validation", "WebServer"})
@Features.Description("Error handler for validation for WebServer")
@Features.Preview
@Features.Flavor(HelidonFlavor.SE)
@Features.InvalidFlavor(HelidonFlavor.MP)
module io.helidon.webserver.validation {
    requires static io.helidon.common.features.api;

    requires io.helidon.http;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.validation;
    requires transitive io.helidon.webserver;

    exports io.helidon.webserver.validation;

    provides io.helidon.webserver.spi.ServerFeatureProvider
            with io.helidon.webserver.validation.WebServerValidationFeatureProvider;
}