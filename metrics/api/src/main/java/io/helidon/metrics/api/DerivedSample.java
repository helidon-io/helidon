/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

class DerivedSample implements Sample.Derived {

    private final double value;
    private final Labeled sample;

    DerivedSample(double value, Labeled reference) {
        this.value = value;
        this.sample = reference;
    }

    @Override
    public double value() {
        return value;
    }

    @Override
    public Labeled sample() {
        return sample;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
