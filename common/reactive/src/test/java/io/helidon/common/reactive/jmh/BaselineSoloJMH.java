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
import io.helidon.common.reactive.Single;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class BaselineSoloJMH {

    @Benchmark
    public void never(Blackhole bh) {
        Multi.never().subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void empty(Blackhole bh) {
        Multi.empty().subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void just(Blackhole bh) {
        Multi.just(1).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void neverSingle(Blackhole bh) {
        Single.never().subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void emptySingle(Blackhole bh) {
        Single.empty().subscribe(new SyncUnboundedJmhSubscriber(bh));
    }

    @Benchmark
    public void justSingle(Blackhole bh) {
        Single.just(1).subscribe(new SyncUnboundedJmhSubscriber(bh));
    }
}
