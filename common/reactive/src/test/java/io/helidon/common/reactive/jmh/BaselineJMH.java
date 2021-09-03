/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.reactive.jmh;

import io.helidon.common.reactive.Multi;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Arrays;
import java.util.Collection;

@State(Scope.Benchmark)
public class BaselineJMH {

    public static void main(String[] args) throws Throwable {
        Options opt = new OptionsBuilder()
                .include(BaselineJMH.class.getSimpleName())
                .include(BaselineSoloJMH.class.getSimpleName())
                .forks(1)
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .build();

        new Runner(opt).run();
    }

    Integer[] array;
    Collection<Integer> collection;

    @Param({"1", "10", "100", "1000", "10000", "100000", "1000000"})
    int count;

    @Setup
    public void setup() {
        array = new Integer[count];
        for (int i = 0; i < count; i++) {
            array[i] = i + 1;
        }
        collection = Arrays.asList(array);
    }

    @Benchmark
    public void justArray(Blackhole bh) {
        Multi.just(array).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void from(Blackhole bh) {
        Multi.create(collection).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void skipZero(Blackhole bh) {
        Multi.just(array).skip(0).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void skipHalf(Blackhole bh) {
        Multi.just(array).skip(count / 2).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void skipAll(Blackhole bh) {
        Multi.just(array).skip(Long.MAX_VALUE).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void map(Blackhole bh) {
        Multi.just(array).map(v -> v + 1).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void filterNone(Blackhole bh) {
        Multi.just(array).filter(v -> v == 0).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void filterHalf(Blackhole bh) {
        Multi.just(array).filter(v -> (v & 1) == 0).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void filterAll(Blackhole bh) {
        Multi.just(array).filter(v -> v == Integer.MAX_VALUE).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void limitZero(Blackhole bh) {
        Multi.just(array).limit(0).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void limitHalf(Blackhole bh) {
        Multi.just(array).limit(count / 2).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void limitAll(Blackhole bh) {
        Multi.just(array).limit(Long.MAX_VALUE).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }
}
