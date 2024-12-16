/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

/**
 * Choices for the meter type for the {@code gc.time} meter.
 */
@Deprecated(since = "4.1", forRemoval = true)
public enum GcTimeType {
    /**
     * Implement the meter as a gauge. This is backward-incompatible with Helidon 4.0.x releases but complies with
     * MicroProfile 5.1.
     */
    GAUGE,

    /**
     * Implement the meter as a counter. This is backward-compatible with Helidon 4.0.x releases but does not comply with
     * MicroProfile 5.1.
     */
    COUNTER
}
