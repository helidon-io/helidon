/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.media.jsonb;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import io.helidon.config.Config;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.spi.MediaSupportProvider;

/**
 * JSON-B support SPI provider.
 */
public class JsonbProvider implements MediaSupportProvider {

    private static final String JSON_B = "json-b";

    @Override
    public MediaSupport create(Config config) {
        JsonbConfig jsonbConfig = new JsonbConfig();
        config.asMap().ifPresent(map -> map.forEach(jsonbConfig::setProperty));
        Jsonb jsonb = JsonbBuilder.create(jsonbConfig);
        return JsonbSupport.create(jsonb);
    }

    @Override
    public String configKey() {
        return JSON_B;
    }
}
