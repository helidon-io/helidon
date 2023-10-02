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

package io.helidon.webserver.benchmark.jmh;

import java.util.Base64;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HuJmhTest {
    private String strOne;
    private String strTwo;

    @Setup
    public void setup() {
        byte[] rand = new byte[26];
        new Random().nextBytes(rand);
        String str = Base64.getEncoder().encodeToString(rand);
        strOne = "/" + str;
        strTwo = str;
    }

    @Benchmark
    public void startsWith(Blackhole bh) {
        boolean one = strOne.startsWith("/");
        boolean two = strTwo.startsWith("/");

        bh.consume(one);
        bh.consume(two);
    }

    @Benchmark
    public void charAt(Blackhole bh) {
        boolean one = strOne.charAt(0) == '/';
        boolean two = strTwo.charAt(0) == '/';

        bh.consume(one);
        bh.consume(two);
    }
}
