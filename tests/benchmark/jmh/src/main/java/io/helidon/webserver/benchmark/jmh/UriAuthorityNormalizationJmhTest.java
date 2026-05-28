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

package io.helidon.webserver.benchmark.jmh;

import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class UriAuthorityNormalizationJmhTest {
    private static final String DNS = "api.example.com";
    private static final String DNS_WITH_PORT = "api.example.com:443";
    private static final String MIXED_CASE_DNS = "Api.Example.COM.";
    private static final String IDN = "bücher.example";
    private static final String IPV4 = "192.0.2.10:8443";
    private static final String IPV6 = "[2001:db8::1]:8443";

    @Benchmark
    public UriHost hostDns() {
        return UriHost.create(DNS);
    }

    @Benchmark
    public UriAuthority authorityDns() {
        return UriAuthority.create(DNS);
    }

    @Benchmark
    public UriAuthority authorityDnsWithPort() {
        return UriAuthority.create(DNS_WITH_PORT);
    }

    @Benchmark
    public UriAuthority authorityMixedCaseDns() {
        return UriAuthority.create(MIXED_CASE_DNS);
    }

    @Benchmark
    public UriAuthority authorityIdn() {
        return UriAuthority.create(IDN);
    }

    @Benchmark
    public UriAuthority authorityIpv4() {
        return UriAuthority.create(IPV4);
    }

    @Benchmark
    public UriAuthority authorityIpv6() {
        return UriAuthority.create(IPV6);
    }

    @Benchmark
    public int authorityPortOrDefault() {
        return UriAuthority.create(DNS).portOrDefault(443);
    }
}
