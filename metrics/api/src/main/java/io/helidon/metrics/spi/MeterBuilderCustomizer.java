/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.metrics.api.Meter;

/**
 * Customizes meter builders before a meter registry uses them to locate or create meters.
 *
 * @since 27.0.0
 */
public interface MeterBuilderCustomizer {

    /**
     * Customizes a meter builder.
     *
     * @param builder meter builder to customize
     * @since 27.0.0
     */
    default void customize(Meter.Builder<?, ?> builder) {
        Objects.requireNonNull(builder);
    }

    /**
     * Customizes a meter builder on behalf of the originating type.
     * <p>
     * The default implementation delegates to {@link #customize(Meter.Builder)}.
     *
     * @param builder meter builder to customize
     * @param origin type which originated the meter
     * @since 27.0.0
     */
    default void customize(Meter.Builder<?, ?> builder, Class<?> origin) {
        Objects.requireNonNull(origin);
        customize(builder);
    }
}
