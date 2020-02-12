/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

/**
 * Composite ConfigSource that accepts list of config sources and and the first one that loads data is used.
 */
class UseFirstAvailableConfigSource implements ConfigSource {

    private final List<? extends ConfigSource> configSources;
    private ConfigSource usedConfigSource;
    private String description;
    private Flow.Publisher<Optional<ObjectNode>> changesPublisher;

    UseFirstAvailableConfigSource(List<? extends ConfigSource> configSources) {
        this.configSources = configSources;

        changesPublisher = Flow.Subscriber::onComplete;
        formatDescription(false);
    }

    UseFirstAvailableConfigSource(ConfigSource... configSources) {
        this(Arrays.asList(configSources));
    }

    @Override
    public void init(ConfigContext context) {
        configSources.forEach(source -> source.init(context));
    }

    @Override
    public Optional<ObjectNode> load() {
        Optional<ObjectNode> result = Optional.empty();
        for (ConfigSource configSource : configSources) {
            result = configSource.load();
            if (result.isPresent()) {
                usedConfigSource = configSource;
                changesPublisher = usedConfigSource.changes();
                break;
            }
        }
        formatDescription(true);
        return result;
    }

    private void formatDescription(boolean loaded) {
        StringBuilder descriptionSB = new StringBuilder();
        boolean availableFormatted = false;
        for (ConfigSource source : configSources) {
            if (descriptionSB.length() > 0) {
                descriptionSB.append("->");
            }
            if (loaded) {
                if (source == usedConfigSource) {
                    availableFormatted = true;
                    descriptionSB.append("*").append(source.description()).append("*");
                } else if (availableFormatted) {
                    descriptionSB.append("/").append(source.description()).append("/");
                } else {
                    descriptionSB.append("(").append(source.description()).append(")");
                }
            } else {
                descriptionSB.append(source.description());
            }
        }
        description = descriptionSB.toString();
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Flow.Publisher<Optional<ObjectNode>> changes() {
        return changesPublisher;
    }

}
