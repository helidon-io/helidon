/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.media.jsonb;

import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.spi.MediaSupportProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for JSON Binding media support.
 */
public class JsonbMediaSupportProvider implements MediaSupportProvider, Weighted {

    /**
     * This class should be only instantiated as part of java {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public JsonbMediaSupportProvider() {
        super();
    }

    @Override
    public String configKey() {
        return "jsonb";
    }

    @Override
    public MediaSupport create(Config config, String name) {
        return JsonbSupport.create(config, name);
    }

    @Override
    public double weight() {
        // very low weight, as this covers all, also lower than Jackson
        return 10;
    }

}
