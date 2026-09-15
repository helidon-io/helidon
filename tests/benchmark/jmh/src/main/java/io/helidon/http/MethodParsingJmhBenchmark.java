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

package io.helidon.http;

import org.openjdk.jmh.annotations.Benchmark;

/**
 * HTTP method parser factory benchmarks.
 */
public class MethodParsingJmhBenchmark {
    /**
     * Parse a canonical method using compatible normalization.
     *
     * @return parsed method
     */
    @Benchmark
    public Method compatibleCanonicalDelete() {
        return Method.create("DELETE");
    }

    /**
     * Parse a lowercase method using compatible normalization.
     *
     * @return parsed method
     */
    @Benchmark
    public Method compatibleLowercaseDelete() {
        return Method.create("delete");
    }

    /**
     * Parse a canonical method while preserving case.
     *
     * @return parsed method
     */
    @Benchmark
    public Method caseSensitiveCanonicalDelete() {
        return Method.createCaseSensitive("DELETE");
    }

    /**
     * Parse a lowercase method while preserving case.
     *
     * @return parsed method
     */
    @Benchmark
    public Method caseSensitiveLowercaseDelete() {
        return Method.createCaseSensitive("delete");
    }
}
