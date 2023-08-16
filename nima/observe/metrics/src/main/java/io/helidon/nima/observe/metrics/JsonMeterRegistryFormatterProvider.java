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
package io.helidon.nima.observe.metrics;

import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;

public class JsonMeterRegistryFormatterProvider implements MeterRegistryFormatterProvider {

    @Override
    public Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                                      MeterRegistry meterRegistry,
                                                      String scopeTagName,
                                                      Iterable<String> scopeSelection,
                                                      Iterable<String> nameSelection) {
        return Optional.of(JsonFormatter.builder(meterRegistry)
                                   .scopeTagName(scopeTagName)
                                   .scopeSelection(scopeSelection)
                                   .meterNameSelection(nameSelection)
                                   .build());
    }
}
