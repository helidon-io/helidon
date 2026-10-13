/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class UriIpv4PredicateJmhBenchmark {
    @Param({"127.0.0.1", "255.255.255.255", "256.1.2.3", "001.002.003.004", "1.2.3.4x", "localhost", "api.example.com"})
    public String host;

    @Benchmark
    public boolean isIpv4Address() {
        return UriValidator.isIpv4Address(host);
    }

    @Benchmark
    public String validateNonIpLiteral() {
        UriValidator.validateNonIpLiteral(host);
        return host;
    }
}
