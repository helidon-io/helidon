/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

import io.helidon.metrics.Sample.Derived;
import io.helidon.metrics.Sample.Labeled;

/**
 * Internal interface prescribing minimum behavior of a snapshot needed to produce output.
 */
interface DisplayableLabeledSnapshot {

    Derived value(double quantile);

    Derived median();

    Labeled max();

    Labeled min();

    Derived mean();

    Derived stdDev();

    Derived sample75thPercentile();

    Derived sample95thPercentile();

    Derived sample98thPercentile();

    Derived sample99thPercentile();

    Derived sample999thPercentile();
}
