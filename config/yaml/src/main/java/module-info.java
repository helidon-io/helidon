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

/**
 * YAML Parser implementation.
 */
@Feature(value = "YAML",
        description = "YAML media type support for config",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Config", "YAML"}
)
module io.helidon.config.yaml {

    requires io.helidon.common;
    requires org.yaml.snakeyaml;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.config;

    exports io.helidon.config.yaml;

    provides io.helidon.config.spi.ConfigParser with io.helidon.config.yaml.YamlConfigParser;
	
}
