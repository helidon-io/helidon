/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.validation;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * A Java {@link java.util.ServiceLoader} provider implementation of a {@link io.helidon.webserver.spi.ServerFeature} that
 * adds error handler for {@link io.helidon.validation.ValidationException} to all server sockets.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 30)
public class WebServerValidationFeatureProvider implements ServerFeatureProvider<WebServerValidationFeature> {
    static final String VALIDATION_TYPE = "validation";

    /**
     * Constructor required by {@link java.util.ServiceLoader}.
     */
    public WebServerValidationFeatureProvider() {
    }

    @Override
    public String configKey() {
        return VALIDATION_TYPE;
    }

    @Override
    public WebServerValidationFeature create(Config config, String name) {
        return new WebServerValidationFeature(config, name);
    }
}
