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
package io.helidon.metrics.microprofile;

import io.micrometer.core.instrument.Meter;
import org.eclipse.microprofile.metrics.Metric;

class MpMetric<M extends Meter> implements Metric {

    private final M delegate;

    MpMetric(M delegate) {
        this.delegate = delegate;
    }

    M delegate() {
        return delegate;
    }
}
