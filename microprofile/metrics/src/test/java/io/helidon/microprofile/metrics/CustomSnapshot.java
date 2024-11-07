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
package io.helidon.microprofile.metrics;

import java.io.OutputStream;

import org.eclipse.microprofile.metrics.Snapshot;

/**
 * Make sure subclass of {@link org.eclipse.microprofile.metrics.Snapshot} without a {@link #bucketValues()} implementation
 * compiles correctly.
 */
class CustomSnapshot extends Snapshot {
    @Override
    public long size() {
        return 0;
    }

    @Override
    public double getMax() {
        return 0;
    }

    @Override
    public double getMean() {
        return 0;
    }

    @Override
    public PercentileValue[] percentileValues() {
        return new PercentileValue[0];
    }

    @Override
    public void dump(OutputStream outputStream) {

    }
}