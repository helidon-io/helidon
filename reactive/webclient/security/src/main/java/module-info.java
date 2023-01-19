/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
import io.helidon.reactive.webclient.security.WebClientSecurityProvider;
import io.helidon.reactive.webclient.spi.WebClientServiceProvider;

/**
 * Helidon WebClient Security.
 */
@Feature(value = "Security",
        description = "Reactive web client support for security",
        in = HelidonFlavor.SE,
        path = {"WebClient", "Security"}
)
module io.helidon.reactive.webclient.security {
    requires static io.helidon.common.features.api;

    requires io.helidon.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.reactive.webclient;

    exports io.helidon.reactive.webclient.security;

    provides WebClientServiceProvider with WebClientSecurityProvider;
}