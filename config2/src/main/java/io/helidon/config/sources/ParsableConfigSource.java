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

package io.helidon.config.sources;

import io.helidon.config.ConfigContext;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigParser;

public abstract class ParsableConfigSource implements EagerSource {
    // media type from builder
    private String mediaType;
    // parser from builder
    private ConfigParser parser;

    protected ParsableConfigSource(String mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public void init(ConfigContext context) {
        if (null == parser) {
            parser = context.findParser(mediaType)
                    .orElseThrow(() -> new ConfigException("Cannot find suitable parser for " + mediaType + " media type."));
        }
    }
}
