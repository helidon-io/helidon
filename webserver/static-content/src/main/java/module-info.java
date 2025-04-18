/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon WebServer Static Content Support.
 */
@Feature(value = "Static Content",
         description = "WebServer Static content support",
         in = HelidonFlavor.SE,
         path = {"WebServer", "Static Content"}
)
module io.helidon.webserver.staticcontent {

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.webserver;
    requires transitive io.helidon.builder.api;
    requires io.helidon;

    exports io.helidon.webserver.staticcontent;

    provides io.helidon.webserver.spi.ServerFeatureProvider
            with io.helidon.webserver.staticcontent.StaticContentFeatureProvider;
}