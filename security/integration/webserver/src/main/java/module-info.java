/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
 * Security integration with Helidon Webserver.
 */
@Feature(value = "WebServer",
        description = "Security integration with web server",
        in = {HelidonFlavor.MP, HelidonFlavor.SE},
        path = {"Security", "Integration", "WebServer"}
)
module io.helidon.security.integration.webserver {
    requires static io.helidon.common.features.api;

    requires java.logging;
    requires jakarta.annotation;

    requires transitive io.helidon.security;
    requires transitive io.helidon.security.util;
    requires io.helidon.reactive.webserver;
    requires io.helidon.security.integration.common;
    requires static io.helidon.config.metadata;

    exports io.helidon.security.integration.webserver;
}
