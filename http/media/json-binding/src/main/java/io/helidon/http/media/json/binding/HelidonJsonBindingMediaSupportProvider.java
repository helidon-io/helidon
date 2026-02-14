/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.http.media.json.binding;

import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.spi.MediaSupportProvider;

/**
 * Media support provider for Helidon JSON Binding media support.
 * <p>
 * This provider creates instances of {@link HelidonJsonBindingSupport} for handling
 * JSON serialization and deserialization in HTTP requests and responses.
 */
public class HelidonJsonBindingMediaSupportProvider implements MediaSupportProvider, Weighted {

    /**
     * This class should be only instantiated as part of java {@link java.util.ServiceLoader}.
     */
    @Deprecated
    public HelidonJsonBindingMediaSupportProvider() {
        super();
    }

    @Override
    public String configKey() {
        return HelidonJsonBindingSupport.HELIDON_JSON_BINDING_TYPE;
    }

    @Override
    public MediaSupport create(Config config, String name) {
        return HelidonJsonBindingSupport.create(config, name);
    }

    @Override
    public double weight() {
        // very low weight, as this covers all, but higher than Jackson
        return 20;
    }
}
