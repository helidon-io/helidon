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

/**
 * A sample with a value, label, and timestamp.
 */
class LabeledSample<T extends Number> {
    private final String label;
    private final long timestamp;
    private final T value;

    LabeledSample(T value, String label, long timestamp) {
        this.value = value;
        this.label = label;
        this.timestamp = timestamp;
    }

    LabeledSample(T value, String label) {
        this(value, label, System.currentTimeMillis());
    }

    static Derived derived(double value, String label, long timestamp) {
        return new Derived(value, label, timestamp);
    }

    static Derived derived(double value, String label) {
        return derived(value, label, System.currentTimeMillis());
    }

    static Derived derived(double value) {
        return derived(value, "");
    }

    T value() {
        return value;
    }

    String label() {
        return label;
    }

    long timestamp() {
        return timestamp;
    }

    /**
     * A labeled sample with a value derived from other values (e.g., a statistic based on other values).
     */
    static class Derived extends LabeledSample<Double> {

        static final Derived ZERO = new Derived(0.0, "");

        private Derived(double value, String label, long timestamp) {
            super(value, label, timestamp);
        }

        private Derived(double value, String label) {
            super(value, label);
        }

        private Derived(double value) {
            this(value, "");
        }
    }
}
