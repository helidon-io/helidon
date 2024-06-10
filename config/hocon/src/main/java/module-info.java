/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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
import io.helidon.config.hocon.HoconConfigParser;

/**
 * Typesafe (Lightbend) Config (HOCON) Parser implementation.
 */
@Feature(value = "HOCON",
        description = "HOCON media type support for config",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Config", "HOCON"}
)
module io.helidon.config.hocon {

    requires io.helidon.common;
    requires typesafe.config;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;

    exports io.helidon.config.hocon;

    provides io.helidon.config.spi.ConfigParser with HoconConfigParser;

}
