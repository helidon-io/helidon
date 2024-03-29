/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Helidon WebClient Security Support.
 */
@Feature(value = "Security",
         description = "WebClient security support",
         in = HelidonFlavor.SE,
         path = {"WebClient", "Security"}
)
module io.helidon.webclient.security {

    requires io.helidon.security.providers.common;
    requires io.helidon.webclient;

    requires transitive io.helidon.security;
    requires transitive io.helidon.webclient.api;

    requires static io.helidon.common.features.api;

    exports io.helidon.webclient.security;

    provides io.helidon.webclient.spi.WebClientServiceProvider
            with io.helidon.webclient.security.WebClientSecurityProvider;

}