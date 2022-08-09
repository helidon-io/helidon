/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.common;

/**
 * Interface to define that this class is a class with weight.
 * One of the uses is for services loaded by a ServiceLoader.
 * <p>
 * A {@code Weighted} with higher weight is more significant than a {@code Weighted} with a
 * lower weight.
 * <p>
 * For cases where weight is the same, implementation must define ordering of such {@code Weighted}.
 * <p>
 * <b>Negative weights are not allowed and services using weight should throw an
 * {@link IllegalArgumentException} if such a weight is used (unless such a service
 * documents the specific usage of a negative weight)</b>
 * <p>
 * A {@code Weighted} with weight {@code 2} is more significant (will be returned before) weight {@code 1}.
 */
public interface Weighted extends Comparable<Weighted> {
    /**
     * Default weight for any weighted component (whether it implements this interface
     * or uses {@link  Weight} annotation).
     */
    double DEFAULT_WEIGHT = 100;

    /**
     * Weight of this class (maybe because it is defined
     * dynamically, so it cannot be defined by an annotation).
     * If not dynamic, you can use the {@code Weight}
     * annotation rather than implementing this interface as long as
     * it is supported by the library using this {@code Weighted}.
     *
     * @return the weight of this service, must be a non-negative number
     */
    default double weight() {
        return DEFAULT_WEIGHT;
    }

    @Override
    default int compareTo(Weighted o) {
        return Double.compare(o.weight(), this.weight());
    }
}
