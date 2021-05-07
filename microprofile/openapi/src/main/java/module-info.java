/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
 *
 */

import javax.enterprise.inject.spi.Extension;

import io.helidon.microprofile.openapi.OpenApiCdiExtension;

module io.helidon.microprofile.openapi {
    requires java.logging;
    
    requires smallrye.open.api;

    requires microprofile.config.api;
    requires io.helidon.microprofile.server;
    requires io.helidon.openapi;

    requires jandex;

    exports io.helidon.microprofile.openapi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.openapi to weld.core.impl, io.helidon.microprofile.cdi;

    provides Extension with OpenApiCdiExtension;
}
