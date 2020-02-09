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

import java.util.Optional;

import io.helidon.config.parsers.PropertiesConfigParser;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.MergingStrategy;

class ConfigSetup {
    ConfigSetup(Config.Builder builder) {

    }

    Optional<ConfigParser> findParser(String mediaType) {
        if ("text/x-java-properties".equals(mediaType)) {
            return Optional.of(new PropertiesConfigParser());
        }
        return Optional.empty();
    }

    public MergingStrategy mergingStrategy() {
        return MergingStrategy.fallback();
    }
}
