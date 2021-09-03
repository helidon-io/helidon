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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Arrays;
import java.util.List;

@State(Scope.Thread)
public class FlatMapIterableJMH {

    public static void main(String[] args) throws Throwable {
        Options opt = new OptionsBuilder()
                .include(FlatMapIterableJMH.class.getSimpleName())
                .forks(1)
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .build();

        new Runner(opt).run();
    }

    @Param({"1", "10", "100", "1000", "10000", "100000", "1000000"})
    int count;

    Multi<Integer> multi;

    @Setup
    public void setup() {
        Integer[] outer = new Integer[count];
        Arrays.fill(outer, 777);
        List<Integer> outerList = Arrays.asList(outer);

        Integer[] inner = new Integer[1_000_000 / count];
        Arrays.fill(inner, 888);
        List<Integer> innerList = Arrays.asList(inner);

        multi = Multi.create(outerList).flatMapIterable(v -> innerList);
    }

    @Benchmark
    public void measure(Blackhole bh) {
        multi.subscribe(new SyncUnboundedJmhSubscriber(bh));
    }
}
