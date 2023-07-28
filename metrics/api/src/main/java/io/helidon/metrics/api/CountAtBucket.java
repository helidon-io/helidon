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
package io.helidon.metrics.api;

import java.util.concurrent.TimeUnit;

/**
 * Representation of a histogram bucket, including the bucket boundary value and the count of observations in that bucket.
 * <p>
 *     The bucket boundary value is an upper bound on the observation values that can occupy the bucket.
 *     That is, an observation occupies a bucket if its value is less than or equal to the bucket's boundary value.
 * </p>
 */
public interface CountAtBucket extends Wrapped {

    /**
     * Returns the bucket boundary.
     *
     * @return bucket boundary value
     */
    double bucket();

    /**
     * Returns the bucket boundary interpreted as a time in nanoseconds andexpressed in the specified
     * {@link java.util.concurrent.TimeUnit}.
     *
     * @param unit time unit in which to express the bucket boundary
     * @return bucket boundary value
     */
    double bucket(TimeUnit unit);

    /**
     * Returns the number of observations in the bucket.
     *
     * @return observation count for the bucket
     */
    double count();
}
