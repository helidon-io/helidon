/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.json;

import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.spi.MediaSupportProvider;

/**
 * Media support provider for Helidon JSON media support.
 * <p>
 * This provider creates instances of {@link JsonSupport} for handling
 * JSON serialization and deserialization in HTTP requests and responses.
 */
public class JsonMediaSupportProvider implements MediaSupportProvider {
    /**
     * This class should be only instantiated as part of java {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public JsonMediaSupportProvider() {
        super();
    }

    @Override
    public String configKey() {
        return JsonSupport.HELIDON_JSON_TYPE;
    }

    @SuppressWarnings("removal")
    @Override
    public MediaSupport create(io.helidon.common.config.Config config, String name) {
        return JsonSupportConfig.builder()
                .name(name)
                .build();
    }

}
