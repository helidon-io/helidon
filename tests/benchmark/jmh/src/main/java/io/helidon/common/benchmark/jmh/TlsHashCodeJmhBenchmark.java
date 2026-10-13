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

package io.helidon.common.benchmark.jmh;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class TlsHashCodeJmhBenchmark {
    @Param({"default", "configured", "disabled"})
    public String configuration;

    private Tls tls;

    @Setup
    public void setup() throws GeneralSecurityException {
        if (configuration.equals("disabled")) {
            tls = Tls.builder().enabled(false).build();
        } else {
            var context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            var parameters = new SSLParameters();
            if (configuration.equals("configured")) {
                parameters.setCipherSuites(new String[] {"TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"});
                parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
                parameters.setApplicationProtocols(new String[] {"h3", "h2", "http/1.1"});
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName("example.com")));
                parameters.setSNIMatchers(List.of());
            } else if (!configuration.equals("default")) {
                throw new IllegalArgumentException("Unknown TLS configuration: " + configuration);
            }
            tls = Tls.builder().sslContext(context).sslParameters(parameters).build();
        }
        if (tlsHashCode() != publicCopyAndVarargs()) {
            throw new IllegalStateException("TLS hash differs from the original public-copy/Objects.hash expression");
        }
    }

    @Benchmark
    public int tlsHashCode() {
        return tls.hashCode();
    }

    @Benchmark
    public int publicCopyAndVarargs() {
        if (!tls.enabled()) {
            return Objects.hash(Tls.class);
        }
        int contextHash = Objects.hash(tls.sslContext());
        var parameters = tls.sslParameters();
        int hash = Objects.hash(parameters.getAlgorithmConstraints(), parameters.getEnableRetransmissions(),
                parameters.getEndpointIdentificationAlgorithm(), parameters.getMaximumPacketSize(),
                parameters.getNeedClientAuth(), parameters.getUseCipherSuitesOrder(), parameters.getWantClientAuth(),
                parameters.getServerNames(), parameters.getSNIMatchers());
        hash = 31 * hash + Arrays.hashCode(parameters.getApplicationProtocols());
        hash = 31 * hash + Arrays.hashCode(parameters.getCipherSuites());
        hash = 31 * hash + Arrays.hashCode(parameters.getProtocols());
        return 31 * contextHash + hash;
    }
}
