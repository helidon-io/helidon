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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * WebServer Access log.
 */
@Feature(value = "Access Log",
        description = "Access log support",
        in = HelidonFlavor.NIMA,
        invalidIn = HelidonFlavor.SE,
        path = {"WebServer", "AccessLog"}
)
module io.helidon.nima.webserver.accesslog {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires io.helidon.nima.webserver;
    requires io.helidon.common.security;

    exports io.helidon.nima.webserver.accesslog;
}