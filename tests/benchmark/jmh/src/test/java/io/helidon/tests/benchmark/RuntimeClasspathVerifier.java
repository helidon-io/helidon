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

package io.helidon.tests.benchmark;

import java.util.Collection;
import java.util.List;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;

/**
 * Rejects a JMH fork whose effective runtime class path differs from the source manifest.
 */
public final class RuntimeClasspathVerifier implements InternalProfiler {
    private boolean verified;

    @Override
    public String getDescription() {
        return "HTTP/3 and QUIC evidence runtime-class-path verifier";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        if (verified) {
            return;
        }
        String expected = System.getProperty(BenchmarkSourceIdentity.RUNTIME_CLASSPATH_SHA256_PROPERTY);
        if (expected == null || expected.isBlank()) {
            throw new IllegalStateException(
                    "HTTP/3 and QUIC evidence JMH fork has no expected runtime-class-path digest");
        }
        String actual;
        try {
            actual = BenchmarkSourceIdentity.runtimeClasspathSha256(Http3QuicEvidenceScope.repositoryRoot());
        } catch (Exception e) {
            throw new IllegalStateException("Could not verify the HTTP/3 and QUIC evidence JMH fork class path", e);
        }
        if (!actual.equals(expected)) {
            throw new IllegalStateException(
                    "HTTP/3 and QUIC evidence JMH fork class path does not match the source manifest");
        }
        verified = true;
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        return List.of();
    }
}
