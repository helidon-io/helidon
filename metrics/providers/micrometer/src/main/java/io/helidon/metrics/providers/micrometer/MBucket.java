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
package io.helidon.metrics.providers.micrometer;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import io.helidon.metrics.api.Bucket;

import io.micrometer.core.instrument.distribution.CountAtBucket;

class MBucket implements Bucket {

    private final CountAtBucket delegate;

    private MBucket(CountAtBucket delegate) {
        this.delegate = delegate;
    }

    static MBucket create(CountAtBucket delegate) {
        return new MBucket(delegate);
    }

    @Override
    public double boundary() {
        return delegate.bucket();
    }

    @Override
    public double boundary(TimeUnit unit) {
        return delegate.bucket(unit);
    }

    @Override
    public long count() {
        return (long) delegate.count();
    }

    @Override
    public <R> R unwrap(Class<? extends R> c) {
        return c.cast(delegate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Simplifies the use of test implementations in unit tests if equals does not insist that the other object
        // also be a MBucket but merely implements Bucket.
        if (!(o instanceof Bucket that)) {
            return false;
        }
        return Objects.equals(delegate.bucket(), that.boundary())
                && Objects.equals((long) delegate.count(), that.count());
    }

    @Override
    public int hashCode() {
        return Objects.hash((long) delegate.bucket(), delegate.count());
    }

    @Override
    public String toString() {
        return new StringJoiner(",", getClass().getSimpleName() + "[", "]")
                .add("boundary=" + boundary())
                .add("count=" + count())
                .toString();
    }
}
