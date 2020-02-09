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

package io.helidon.config;

import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;

public class ConfigSourceSetup {
    private final ConfigSource theSource;
    private final boolean isOptional;
    private final PollingStrategy pollingStrategy;
    private final RetryPolicy retryPolicy;

    ConfigSourceSetup(ConfigSource theSource,
                      boolean isOptional,
                      PollingStrategy pollingStrategy,
                      RetryPolicy retryPolicy) {
        this.theSource = theSource;
        this.isOptional = isOptional;
        this.pollingStrategy = pollingStrategy;
        this.retryPolicy = retryPolicy;
    }

    public ConfigSource theSource() {
        return theSource;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public PollingStrategy pollingStrategy() {
        return pollingStrategy;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }
}
