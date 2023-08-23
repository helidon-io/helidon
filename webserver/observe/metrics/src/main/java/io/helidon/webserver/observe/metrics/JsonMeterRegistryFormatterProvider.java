/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;

/**
 * JSON formatter provider.
 */
public class JsonMeterRegistryFormatterProvider implements MeterRegistryFormatterProvider {

    /**
     * Creates a new instance for service loading.
     */
    public JsonMeterRegistryFormatterProvider() {
    }

    @Override
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MeterRegistry meterRegistry,
                                                      Optional<String> scopeTagName,
                                                      Iterable<String> scopeSelection,
                                                      Iterable<String> nameSelection) {
        return mediaType.type().equals(MediaTypes.APPLICATION_JSON.type())
                && mediaType.subtype().equals(MediaTypes.APPLICATION_JSON.subtype())
                ? Optional.of(create(meterRegistry,
                                     scopeTagName,
                                     scopeSelection,
                                     nameSelection))
                : Optional.empty();
    }

    private JsonFormatter create(MeterRegistry meterRegistry,
                                 Optional<String> scopeTagName,
                                 Iterable<String> scopeSelection,
                                 Iterable<String> nameSelection) {
        JsonFormatter.Builder builder = JsonFormatter.builder(meterRegistry)
                .scopeSelection(scopeSelection)
                .meterNameSelection(nameSelection);
        scopeTagName.ifPresent(builder::scopeTagName);
        return builder.build();
    }
}
