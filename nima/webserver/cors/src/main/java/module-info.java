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
 * Helidon WebServer CORS support.
 */
@Feature(value = "Nima CORS",
        description = "CORS support for Nima WebServer",
        in = HelidonFlavor.NIMA,
        invalidIn = HelidonFlavor.SE,
        path = {"WebServer", "CORS"})
module io.helidon.nima.webserver.cors {
    requires static io.helidon.common.features.api;

    requires java.logging;

    requires transitive io.helidon.cors;
    requires io.helidon.nima.webserver;

    exports io.helidon.nima.webserver.cors;
}