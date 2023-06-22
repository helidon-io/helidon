/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Base implementation of {@link Sample.Labeled}.
 */
public class LabeledSample implements Sample.Labeled {
    private final double value;
    private final String label;
    private final long timestamp;

    /**
     * Create a sample.
     *
     * @param value recorded value
     * @param label label
     * @param timestamp timestamp
     */
    protected LabeledSample(double value, String label, long timestamp) {
        this.value = value;
        this.label = label;
        this.timestamp = timestamp;
    }

    @Override
    public double value() {
        return value;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
