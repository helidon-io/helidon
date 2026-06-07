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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.tls.Tls;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniRequestSupport;
import io.helidon.webserver.VirtualHostConfig;
import io.helidon.webserver.internal.SniBenchmarkSupport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ServerSniJmhTest {
    private static final String SOCKET_NAME = "jmh";
    private static final String EXACT_HOST = "api.example.com";
    private static final String WILDCARD_HOST = "admin.wild.example.com";
    private static final String FALLBACK_HOST = "other.example.com";
    private static final Headers HEADERS = ServerRequestHeaders.create();
    private static final HttpPrologue PROLOGUE = HttpPrologue.create("HTTP/1.1",
                                                                     "HTTP",
                                                                     "1.1",
                                                                     Method.GET,
                                                                     "/plaintext",
                                                                     true);
    private static final Tls TLS = Tls.builder()
            .trustAll(true)
            .build();

    private byte[] noSniClientHello;
    private byte[] exactClientHello;
    private byte[] wildcardClientHello;
    private byte[] fragmentedExactClientHello;
    private SniBenchmarkSupport.Registry exactRegistry;
    private SniBenchmarkSupport.Registry wildcardRegistry;
    private SniBenchmarkSupport.Registry manyVirtualHostsRegistry;
    private SniContext exactSniContext;
    private SniContext fallbackSniContext;
    private SniContext manyFallbackSniContext;

    @Setup
    public void setup() {
        byte[] exactHandshake = clientHello(EXACT_HOST);
        noSniClientHello = record(clientHelloWithoutSni());
        exactClientHello = record(exactHandshake);
        wildcardClientHello = record(clientHello(WILDCARD_HOST));
        fragmentedExactClientHello = concat(record(exactHandshake, 0, 11),
                                            record(exactHandshake, 11, exactHandshake.length - 11));

        exactRegistry = registry(EXACT_HOST);
        wildcardRegistry = registry("*.wild.example.com");
        manyVirtualHostsRegistry = manyVirtualHostsRegistry();

        exactSniContext = SniBenchmarkSupport.select(exactRegistry, EXACT_HOST);
        fallbackSniContext = SniBenchmarkSupport.selectWithoutSni(exactRegistry);
        manyFallbackSniContext = SniBenchmarkSupport.selectWithoutSni(manyVirtualHostsRegistry);
    }

    @Benchmark
    public SniContext clientHelloParserNoSniFallback() throws IOException {
        return SniBenchmarkSupport.readParserAndSelect(noSniClientHello, exactRegistry);
    }

    @Benchmark
    public SniContext clientHelloParserExactSni() throws IOException {
        return SniBenchmarkSupport.readParserAndSelect(exactClientHello, exactRegistry);
    }

    @Benchmark
    public SniContext clientHelloParserWildcardSni() throws IOException {
        return SniBenchmarkSupport.readParserAndSelect(wildcardClientHello, wildcardRegistry);
    }

    @Benchmark
    public SniContext clientHelloParserFragmentedExactSni() throws IOException {
        return SniBenchmarkSupport.readParserAndSelect(fragmentedExactClientHello, exactRegistry);
    }

    @Benchmark
    public SniContext clientHelloParserExactSniManyVirtualHosts() throws IOException {
        return SniBenchmarkSupport.readParserAndSelect(exactClientHello, manyVirtualHostsRegistry);
    }

    @Benchmark
    public SniContext authorityFallbackAllowed() {
        SniRequestSupport.validateAuthority(fallbackSniContext, PROLOGUE, HEADERS, FALLBACK_HOST);
        return fallbackSniContext;
    }

    @Benchmark
    public SniContext authorityExactAllowed() {
        SniRequestSupport.validateAuthority(exactSniContext, PROLOGUE, HEADERS, EXACT_HOST);
        return exactSniContext;
    }

    @Benchmark
    public SniContext.AuthorityCheck authorityMismatchCheck() {
        return exactSniContext.checkAuthority("admin.example.com");
    }

    @Benchmark
    public SniContext.AuthorityCheck fallbackAuthorityExactConfiguredCheck() {
        return fallbackSniContext.checkAuthority(EXACT_HOST);
    }

    @Benchmark
    public SniContext.AuthorityCheck fallbackAuthorityWildcardConfiguredCheck() {
        return fallbackSniContext.checkAuthority(WILDCARD_HOST);
    }

    @Benchmark
    public SniContext.AuthorityCheck fallbackAuthorityManyVirtualHostsCheck() {
        return manyFallbackSniContext.checkAuthority("host127.example.com");
    }

    private static SniBenchmarkSupport.Registry registry(String... hosts) {
        ListenerConfig.Builder builder = ListenerConfig.builder()
                .tls(TLS);
        for (String host : hosts) {
            builder.addVirtualHost(virtualHost(host));
        }
        return SniBenchmarkSupport.registry(SOCKET_NAME, builder.build(), TLS);
    }

    private static SniBenchmarkSupport.Registry manyVirtualHostsRegistry() {
        ListenerConfig.Builder builder = ListenerConfig.builder()
                .tls(TLS)
                .addVirtualHost(virtualHost(EXACT_HOST));
        for (int i = 0; i < 128; i++) {
            builder.addVirtualHost(virtualHost("host" + i + ".example.com"));
        }
        return SniBenchmarkSupport.registry(SOCKET_NAME, builder.build(), TLS);
    }

    private static VirtualHostConfig virtualHost(String host) {
        return VirtualHostConfig.builder()
                .host(host)
                .tls(TLS)
                .build();
    }

    private static byte[] clientHello(String host) {
        byte[] hostBytes = host.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream sni = new ByteArrayOutputStream();
        writeShort(sni, 3 + hostBytes.length);
        sni.write(0);
        writeShort(sni, hostBytes.length);
        sni.writeBytes(hostBytes);

        ByteArrayOutputStream extension = new ByteArrayOutputStream();
        writeShort(extension, 0);
        writeShort(extension, sni.size());
        extension.writeBytes(sni.toByteArray());

        ByteArrayOutputStream body = baseClientHelloBody();
        writeShort(body, extension.size());
        body.writeBytes(extension.toByteArray());

        return handshake(body.toByteArray());
    }

    private static byte[] clientHelloWithoutSni() {
        ByteArrayOutputStream body = baseClientHelloBody();
        writeShort(body, 0);
        return handshake(body.toByteArray());
    }

    private static ByteArrayOutputStream baseClientHelloBody() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0x03);
        body.write(0x03);
        body.writeBytes(new byte[32]);
        body.write(0);
        writeShort(body, 2);
        body.write(0x13);
        body.write(0x01);
        body.write(1);
        body.write(0);
        return body;
    }

    private static byte[] handshake(byte[] body) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(1);
        writeMedium(result, body.length);
        result.writeBytes(body);
        return result.toByteArray();
    }

    private static byte[] record(byte[] handshake) {
        return record(handshake, 0, handshake.length);
    }

    private static byte[] record(byte[] handshake, int offset, int length) {
        byte[] fragment = new byte[length];
        System.arraycopy(handshake, offset, fragment, 0, length);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(22);
        result.write(0x03);
        result.write(0x03);
        writeShort(result, fragment.length);
        result.writeBytes(fragment);
        return result.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes(first);
        result.writeBytes(second);
        return result.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream stream, int value) {
        stream.write((value >>> 8) & 0xFF);
        stream.write(value & 0xFF);
    }

    private static void writeMedium(ByteArrayOutputStream stream, int value) {
        stream.write((value >>> 16) & 0xFF);
        stream.write((value >>> 8) & 0xFF);
        stream.write(value & 0xFF);
    }
}
