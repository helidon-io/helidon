/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.concurrent.TimeUnit;

/**
 * TestClock class.
 */
public class TestClock implements Clock {
    private long nanoTime;
    private long milliTime;

    private TestClock(long nanoTime, long milliTime) {
        this.nanoTime = nanoTime;
        this.milliTime = milliTime;
    }

    static TestClock create() {
        return new TestClock(0, System.currentTimeMillis());
    }

    static TestClock create(long nanos, long millis) {
        return new TestClock(nanos, millis);
    }

    @Override
    public long nanoTick() {
        return nanoTime;
    }

    @Override
    public long milliTime() {
        return milliTime;
    }

    void addNanos(long duration, TimeUnit unit) {
        nanoTime += unit.toNanos(duration);
    }

    void addMillis(long duration, TimeUnit unit) {
        milliTime += unit.toMillis(duration);
    }

    void add(long duration, TimeUnit unit) {
        addNanos(duration, unit);
        addMillis(duration, unit);
    }

    void setNanos(long nanos) {
        this.nanoTime = nanos;
    }

    void setMillis(long millis) {
        this.milliTime = millis;
    }

}
