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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * HTTP method creation benchmarks.
 */
@State(Scope.Thread)
public class MethodCreateJmhBenchmark {
    @Param(Method.GET_NAME)
    private String canonicalGet;

    @Param(Method.POST_NAME)
    private String canonicalPost;

    @Param("get")
    private String lowercaseGet;

    /**
     * Create the canonical GET method.
     *
     * @return GET method
     */
    @Benchmark
    public Method canonicalGet() {
        return Method.create(canonicalGet);
    }

    /**
     * Create the canonical POST method.
     *
     * @return POST method
     */
    @Benchmark
    public Method canonicalPost() {
        return Method.create(canonicalPost);
    }

    /**
     * Create a lowercase GET method token.
     *
     * @return lowercase GET method
     */
    @Benchmark
    public Method lowercaseGet() {
        return Method.create(lowercaseGet);
    }
}
