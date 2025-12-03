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

package io.helidon.declarative.codegen.metrics;

import io.helidon.common.types.TypeName;

final class MetricsTypes {
    static final TypeName ANNOTATION_TAG = TypeName.create("io.helidon.metrics.api.Metrics.Tag");
    static final TypeName ANNOTATION_TAGS = TypeName.create("io.helidon.metrics.api.Metrics.Tags");
    static final TypeName ANNOTATION_COUNTED = TypeName.create("io.helidon.metrics.api.Metrics.Counted");
    static final TypeName ANNOTATION_TIMED = TypeName.create("io.helidon.metrics.api.Metrics.Timed");
    static final TypeName ANNOTATION_GAUGE = TypeName.create("io.helidon.metrics.api.Metrics.Gauge");

    static final TypeName METER_REGISTRY = TypeName.create("io.helidon.metrics.api.MeterRegistry");
    static final TypeName GAUGE = TypeName.create("io.helidon.metrics.api.Gauge");
    static final TypeName COUNTER = TypeName.create("io.helidon.metrics.api.Counter");
    static final TypeName TIMER = TypeName.create("io.helidon.metrics.api.Timer");
    static final TypeName TAG = TypeName.create("io.helidon.metrics.api.Tag");

    private MetricsTypes() {
    }
}
