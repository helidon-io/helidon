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
package io.helidon.metrics.spi;

import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;

/**
 * Behavior for providers of meter registry formatters, which (if then can) furnish a formatter given a
 * {@link io.helidon.common.media.type.MediaType}.
 *
 * <p>
 *     We use a provider approach so code can obtain and run formatters that might depend heavily on particular implementations
 *     without the calling code having to share that heavy dependency.
 * </p>
 */
public interface MeterRegistryFormatterProvider {

    /**
     * Returns, if possible, a {@link io.helidon.metrics.api.MeterRegistryFormatter} capable of preparing output according to
     * the specified {@link io.helidon.common.media.type.MediaType}.
     * @param mediaType media type of the desired output
     * @param meterRegistry {@link io.helidon.metrics.api.MeterRegistry} from which to gather data
     * @param scopeTagName tag name used to record scope
     * @param scopeSelection scope names to format; empty means no scope-based restriction
     * @param nameSelection meter names to format; empty means no name-based restriction
     * @return compatible formatter; empty if none
     */
    Optional<MeterRegistryFormatter> formatter(MediaType mediaType,
                                               MeterRegistry meterRegistry,
                                               Optional<String> scopeTagName,
                                               Iterable<String> scopeSelection,
                                               Iterable<String> nameSelection);
}
