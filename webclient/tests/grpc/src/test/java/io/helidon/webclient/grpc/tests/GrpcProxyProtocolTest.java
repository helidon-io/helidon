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

package io.helidon.webclient.grpc.tests;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.helidon.config.Config;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionListener;
import io.helidon.webclient.grpc.ClientUriSuppliers;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.ProxyProtocolV2Data;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcConnectionContext;
import io.helidon.webserver.grpc.GrpcRouting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GrpcProxyProtocolTest {
    @TempDir
    public Path tempDir;

    @Test
    public void testProxyProtocolV1OverTcp() throws IOException {
        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .tls(t -> t.enabled(false))
            .bindAddress(new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0))
            .addRouting(GrpcRouting.builder().unary(Proxy.getDescriptor(),
                    "ProxyProtocolService",
                    "GetData",
                    GrpcProxyProtocolTest::getProxyData))
            .build();
        server.start();

        GrpcClient grpcClient = GrpcClient.create(b -> b
            .config(Config.create().get("grpc-client"))
            .tls(t -> t.enabled(false))
            .connectionListener(v1Listener("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"))
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .baseAddress(new InetSocketAddress(Inet4Address.getLoopbackAddress(), server.port())));
        var service = ProxyProtocolServiceGrpc.newBlockingStub(grpcClient.channel());
        var response = service.getData(Empty.getDefaultInstance());
        assertThat(response.getVersion(), is(1));
        assertThat(response.getSourceAddress(), is("192.168.0.1"));
        assertThat(response.getDestinationAddress(), is("192.168.0.11"));
        assertThat(response.getSourcePort(), is(56324));
        assertThat(response.getDestinationPort(), is(443));
        assertThat(response.getProtocol(), is(ProxyProtocolData.Protocol.TCP.name()));
        assertThat(response.getAddressFamily(), is(ProxyProtocolData.Family.IPv4.name()));

        server.stop();
    }

    @Test
    public void testProxyProtocolV2OverTcp() throws IOException {
        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .tls(t -> t.enabled(false))
            .bindAddress(new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0))
            .addRouting(GrpcRouting.builder().unary(Proxy.getDescriptor(),
                "ProxyProtocolService",
                "GetData",
                GrpcProxyProtocolTest::getProxyData))
            .build();
        server.start();

        GrpcClient grpcClient = GrpcClient.create(b -> b
            .config(Config.create().get("grpc-client"))
            .tls(t -> t.enabled(false))
            .connectionListener(v2Listener(new int[] {
                0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, // header
                0x21, // version 1, PROXY command
                0x11, // AF_INET, STREAM
                0x00, 0x20, // 32 bytes remaining,
                // Addresses
                0xC0, 0xA8, 0x00, 0x01, // source IPv4 address, 192.168.0.1
                0x7F, 0x00, 0x00, 0x01, // destination IPv4 address, 127.0.0.1
                0xDE, 0xAD, // source IP address, 57005
                0xC0, 0xDE, // dest IP address, 49374
                // First TLV
                0x05, // PP2_TYPE_UNIQUE_ID
                0x00, 0x0B, // Length
                0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20, 0x77, 0x6F, 0x72, 0x6C, 0x64, // "Hello world" bytes
                // Second TLV
                0xE0, // PP2_TYPE_MIN_CUSTOM
                0x00, 0x03, // Length
                0x61, 0x62, 0x63 // "abc"
            }))
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .baseAddress(new InetSocketAddress(Inet4Address.getLoopbackAddress(), server.port())));
        var service = ProxyProtocolServiceGrpc.newBlockingStub(grpcClient.channel());
        var response = service.getData(Empty.getDefaultInstance());
        assertThat(response.getVersion(), is(2));
        assertThat(response.getSourceAddress(), is("192.168.0.1"));
        assertThat(response.getDestinationAddress(), is("127.0.0.1"));
        assertThat(response.getSourcePort(), is(57005));
        assertThat(response.getDestinationPort(), is(49374));
        assertThat(response.getProtocol(), is(ProxyProtocolData.Protocol.TCP.name()));
        assertThat(response.getAddressFamily(), is(ProxyProtocolData.Family.IPv4.name()));
        assertThat(response.getCommand(), is(ProxyProtocolV2Data.Command.PROXY.name()));
        assertThat(response.getSourceSocketAddress(), is("/192.168.0.1:57005"));
        assertThat(response.getDestinationSocketAddress(), is("/127.0.0.1:49374"));
        assertThat(response.getTlvsList().get(0).getType(), is(0x05));
        assertThat(response.getTlvsList().get(1).getType(), is(0xE0));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), response.getTlvsList().get(1).getData().toByteArray());

        server.stop();
    }

    @Test
    public void testProxyProtocolV1OverUds() throws IOException {
        Files.createDirectories(tempDir);
        final var bindAddress = UnixDomainSocketAddress.of(tempDir.resolve("uds.socket"));

        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .tls(t -> t.enabled(false))
            .bindAddress(bindAddress)
            .addRouting(GrpcRouting.builder().unary(Proxy.getDescriptor(),
                "ProxyProtocolService",
                "GetData",
                GrpcProxyProtocolTest::getProxyData))
            .build();
        server.start();

        GrpcClient grpcClient = GrpcClient.create(b -> b
            .config(Config.create().get("grpc-client"))
            .tls(t -> t.enabled(false))
            .connectionListener(v1Listener("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"))
            .baseUri(URI.create("http://localhost:0"))
            .clientUriSupplier(ClientUriSuppliers.SingleSupplier.create(ClientUri.create()
                .scheme("http")
                .host("localhost")
                .port(0)
                .path("/")))
            .baseAddress(bindAddress));
        var service = ProxyProtocolServiceGrpc.newBlockingStub(grpcClient.channel());
        var response = service.getData(Empty.getDefaultInstance());
        assertThat(response.getVersion(), is(1));
        assertThat(response.getSourceAddress(), is("192.168.0.1"));
        assertThat(response.getDestinationAddress(), is("192.168.0.11"));
        assertThat(response.getSourcePort(), is(56324));
        assertThat(response.getDestinationPort(), is(443));
        assertThat(response.getProtocol(), is(ProxyProtocolData.Protocol.TCP.name()));
        assertThat(response.getAddressFamily(), is(ProxyProtocolData.Family.IPv4.name()));

        server.stop();
    }

    @Test
    public void testProxyProtocolV2OverUds() throws IOException {
        Files.createDirectories(tempDir);
        final var bindAddress = UnixDomainSocketAddress.of(tempDir.resolve("uds.socket"));

        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .tls(t -> t.enabled(false))
            .bindAddress(bindAddress)
            .addRouting(GrpcRouting.builder().unary(Proxy.getDescriptor(),
                "ProxyProtocolService",
                "GetData",
                GrpcProxyProtocolTest::getProxyData))
            .build();
        server.start();

        GrpcClient grpcClient = GrpcClient.create(b -> b
            .config(Config.create().get("grpc-client"))
            .tls(t -> t.enabled(false))
            .connectionListener(v2Listener(new int[] {
                0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, // header
                0x21, // version 1, PROXY command
                0x11, // AF_INET, STREAM
                0x00, 0x20, // 32 bytes remaining,
                // Addresses
                0xC0, 0xA8, 0x00, 0x01, // source IPv4 address, 192.168.0.1
                0x7F, 0x00, 0x00, 0x01, // destination IPv4 address, 127.0.0.1
                0xDE, 0xAD, // source IP address, 57005
                0xC0, 0xDE, // dest IP address, 49374
                // First TLV
                0x05, // PP2_TYPE_UNIQUE_ID
                0x00, 0x0B, // Length
                0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20, 0x77, 0x6F, 0x72, 0x6C, 0x64, // "Hello world" bytes
                // Second TLV
                0xE0, // PP2_TYPE_MIN_CUSTOM
                0x00, 0x03, // Length
                0x61, 0x62, 0x63 // "abc"
            }))
            .baseUri(URI.create("http://localhost:0"))
            .clientUriSupplier(ClientUriSuppliers.SingleSupplier.create(ClientUri.create()
                .scheme("http")
                .host("localhost")
                .port(0)
                .path("/")))
            .baseAddress(bindAddress));
        var service = ProxyProtocolServiceGrpc.newBlockingStub(grpcClient.channel());
        var response = service.getData(Empty.getDefaultInstance());
        assertThat(response.getVersion(), is(2));
        assertThat(response.getSourceAddress(), is("192.168.0.1"));
        assertThat(response.getDestinationAddress(), is("127.0.0.1"));
        assertThat(response.getSourcePort(), is(57005));
        assertThat(response.getDestinationPort(), is(49374));
        assertThat(response.getProtocol(), is(ProxyProtocolData.Protocol.TCP.name()));
        assertThat(response.getAddressFamily(), is(ProxyProtocolData.Family.IPv4.name()));
        assertThat(response.getCommand(), is(ProxyProtocolV2Data.Command.PROXY.name()));
        assertThat(response.getSourceSocketAddress(), is("/192.168.0.1:57005"));
        assertThat(response.getDestinationSocketAddress(), is("/127.0.0.1:49374"));
        assertThat(response.getTlvsCount(), is(2));
        assertThat(response.getTlvsList().get(0).getType(), is(0x05));
        assertThat(response.getTlvsList().get(1).getType(), is(0xE0));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), response.getTlvsList().get(1).getData().toByteArray());

        server.stop();
    }

    private ConnectionListener v1Listener(final String proxyHeader) {
        return ConnectionListener.writeBytes(proxyHeader.getBytes(StandardCharsets.UTF_8));
    }

    private ConnectionListener v2Listener(final int[] proxyHeader) {
        final byte[] proxyHeaderBytes = new byte[proxyHeader.length];
        for (int i = 0; i < proxyHeader.length; i++) {
            proxyHeaderBytes[i] = (byte) proxyHeader[i];
        }
        return ConnectionListener.writeBytes(proxyHeaderBytes);
    }

    static void getProxyData(Empty req, StreamObserver<Proxy.ProxyProtocolDataMessage> streamObserver) {
        final var context = ContextKeys.HELIDON_CONTEXT.get();
        final var connContext = context.get(GrpcConnectionContext.class, GrpcConnectionContext.class).get();
        final var data = connContext.proxyProtocolData().get();
        final var response = Proxy.ProxyProtocolDataMessage.newBuilder()
            .setVersion(1)
            .setSourceAddress(data.sourceAddress())
            .setSourcePort(data.sourcePort())
            .setDestinationAddress(data.destAddress())
            .setDestinationPort(data.destPort())
            .setAddressFamily(data.family().name())
            .setProtocol(data.protocol().name());
        if (data instanceof ProxyProtocolV2Data v2) {
            response.setVersion(2)
                .setCommand(v2.command().name())
                .setSourceSocketAddress(v2.sourceSocketAddress().toString())
                .setDestinationSocketAddress(v2.destSocketAddress().toString());
            for (var tlv : v2.tlvs()) {
                final var tlvBuilder = Proxy.ProxyProtocolTlvMessage.newBuilder()
                    .setType(tlv.type());
                if (tlv instanceof ProxyProtocolV2Data.Tlv.Unregistered unregistered) {
                    tlvBuilder.setData(ByteString.copyFrom(unregistered.value()));
                }
                response.addTlvs(tlvBuilder.build());
            }
        }
        streamObserver.onNext(response.build());
        streamObserver.onCompleted();
    }
}
