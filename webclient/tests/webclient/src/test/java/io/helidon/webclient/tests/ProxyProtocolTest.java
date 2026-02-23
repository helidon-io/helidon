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

package io.helidon.webclient.tests;

import io.helidon.http.Status;
import io.helidon.webclient.api.ConnectionListener;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.ProxyProtocolV2Data;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProxyProtocolTest {
    @Test
    public void v1Tcp4() {
        var server = WebServer.builder()
            .enableProxyProtocol(true)
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                var data = req.proxyProtocolData().get();
                var sb = new StringBuilder();
                sb.append(data.protocol().name()).append('\n')
                        .append(data.family().name()).append('\n')
                        .append(data.sourceAddress()).append('\n')
                        .append(data.destAddress()).append('\n')
                        .append(data.sourcePort()).append('\n')
                        .append(data.destPort());
                res.status(Status.OK_200).send(sb.toString());
            }))
            .build();
        server.start();

        var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionListener(v1Listener("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv4.name()));
            assertThat(contents[2], is("192.168.0.1"));
            assertThat(contents[3], is("192.168.0.11"));
            assertThat(contents[4], is("56324"));
            assertThat(contents[5], is("443"));
        }

        server.stop();
    }

    @Test
    public void v1Tcp6() {
        var server = WebServer.builder()
            .enableProxyProtocol(true)
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                var data = req.proxyProtocolData().get();
                var sb = new StringBuilder();
                sb.append(data.protocol().name()).append('\n')
                    .append(data.family().name()).append('\n')
                    .append(data.sourceAddress()).append('\n')
                    .append(data.destAddress()).append('\n')
                    .append(data.sourcePort()).append('\n')
                    .append(data.destPort());
                res.status(Status.OK_200).send(sb.toString());
            }))
            .build();
        server.start();

        var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionListener(v1Listener("PROXY TCP6 0000:0000:0000:0000:0000:0000:0000:0000 FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF 56324 443\r\n"))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv6.name()));
            assertThat(contents[2], is("0000:0000:0000:0000:0000:0000:0000:0000"));
            assertThat(contents[3], is("FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF"));
            assertThat(contents[4], is("56324"));
            assertThat(contents[5], is("443"));
        }

        server.stop();
    }

    @Test
    public void v2Ipv4() {
        var server = WebServer.builder()
            .enableProxyProtocol(true)
            .idleConnectionTimeout(Duration.ofSeconds(1))
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                var data = req.proxyProtocolData().get();
                var data2 = (ProxyProtocolV2Data) data;
                var sb = new StringBuilder();
                sb.append(data.protocol().name()).append('\n')
                    .append(data.family().name()).append('\n')
                    .append(data.sourceAddress()).append('\n')
                    .append(data.destAddress()).append('\n')
                    .append(data.sourcePort()).append('\n')
                    .append(data.destPort()).append('\n')
                    .append(data2.command().name()).append('\n')
                    .append(data2.sourceSocketAddress().toString()).append('\n')
                    .append(data2.destSocketAddress().toString()).append('\n')
                    .append(data2.tlvs().size());
                res.status(Status.OK_200).send(sb.toString());
            }))
            .build();
        server.start();

        var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionListener(v2Listener(new int[] {
                0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, // header
                0x21, // version 1, PROXY command
                0x11, // AF_INET, STREAM
                0x00, 0x0C, // 12 bytes remaining,
                0xC0, 0xA8, 0x00, 0x01, // source IPv4 address, 192.168.0.1
                0x7F, 0x00, 0x00, 0x01, // destination IPv4 address, 127.0.0.1
                0xDE, 0xAD, // source IP address, 57005
                0xC0, 0xDE, // dest IP address, 49374
            }))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv4.name()));
            assertThat(contents[2], is("192.168.0.1"));
            assertThat(contents[3], is("127.0.0.1"));
            assertThat(contents[4], is("57005"));
            assertThat(contents[5], is("49374"));
            assertThat(contents[6], is(ProxyProtocolV2Data.Command.PROXY.name()));
            assertThat(contents[7], is("/192.168.0.1:57005"));
            assertThat(contents[8], is("/127.0.0.1:49374"));
            assertThat(contents[9], is("0"));
        }

        server.stop();
    }

    @Test
    public void v2Ipv6() {
        var server = WebServer.builder()
            .enableProxyProtocol(true)
            .idleConnectionTimeout(Duration.ofSeconds(1))
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                var data = req.proxyProtocolData().get();
                var data2 = (ProxyProtocolV2Data) data;
                var sb = new StringBuilder();
                sb.append(data.protocol().name()).append('\n')
                    .append(data.family().name()).append('\n')
                    .append(data.sourceAddress()).append('\n')
                    .append(data.destAddress()).append('\n')
                    .append(data.sourcePort()).append('\n')
                    .append(data.destPort()).append('\n')
                    .append(data2.command().name()).append('\n')
                    .append(data2.sourceSocketAddress().toString()).append('\n')
                    .append(data2.destSocketAddress().toString()).append('\n')
                    .append(data2.tlvs().size());
                res.status(Status.OK_200).send(sb.toString());
            }))
            .build();
        server.start();

        var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionListener(v2Listener(new int[] {
                0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, // header
                0x21, // version 1, PROXY command
                0x21, // AF_INET6, STREAM
                0x00, 0x24, // 36 bytes remaining,
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, // source IPv6 address, 0:1:2:3:4:5:6:7:8:9:10:11:12:13:14:15
                0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, // destination IPv6 address, FFFF::FFFF
                0xDE, 0xAD, // source IP address, 57005
                0xC0, 0xDE, // dest IP address, 49374
            }))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv6.name()));
            assertThat(contents[2], is("1:203:405:607:809:a0b:c0d:e0f"));
            assertThat(contents[3], is("ffff:0:0:0:0:0:0:ffff"));
            assertThat(contents[4], is("57005"));
            assertThat(contents[5], is("49374"));
            assertThat(contents[6], is(ProxyProtocolV2Data.Command.PROXY.name()));
            assertThat(contents[7], is("/[1:203:405:607:809:a0b:c0d:e0f]:57005"));
            assertThat(contents[8], is("/[ffff:0:0:0:0:0:0:ffff]:49374"));
            assertThat(contents[9], is("0"));
        }

        server.stop();
    }

    @Test
    public void v2Ipv4WithTlvs() {
        var server = WebServer.builder()
            .enableProxyProtocol(true)
            .idleConnectionTimeout(Duration.ofSeconds(1))
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                var data = req.proxyProtocolData().get();
                var data2 = (ProxyProtocolV2Data) data;
                var sb = new StringBuilder();
                sb.append(data.protocol().name()).append('\n')
                    .append(data.family().name()).append('\n')
                    .append(data.sourceAddress()).append('\n')
                    .append(data.destAddress()).append('\n')
                    .append(data.sourcePort()).append('\n')
                    .append(data.destPort()).append('\n')
                    .append(data2.command().name()).append('\n')
                    .append(data2.sourceSocketAddress().toString()).append('\n')
                    .append(data2.destSocketAddress().toString()).append('\n')
                    .append(data2.tlvs().size()).append('\n')
                    .append(data2.tlvs().get(0).type()).append('\n')
                    .append(new String(((ProxyProtocolV2Data.Tlv.UniqueId) data2.tlvs().get(0)).id(), StandardCharsets.UTF_8)).append('\n')
                    .append(data2.tlvs().get(1).type()).append('\n')
                    .append(new String(((ProxyProtocolV2Data.Tlv.Unregistered) data2.tlvs().get(1)).value(), StandardCharsets.UTF_8)).append('\n');
                res.status(Status.OK_200).send(sb.toString());
            }))
            .build();
        server.start();

        var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
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
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv4.name()));
            assertThat(contents[2], is("192.168.0.1"));
            assertThat(contents[3], is("127.0.0.1"));
            assertThat(contents[4], is("57005"));
            assertThat(contents[5], is("49374"));
            assertThat(contents[6], is(ProxyProtocolV2Data.Command.PROXY.name()));
            assertThat(contents[7], is("/192.168.0.1:57005"));
            assertThat(contents[8], is("/127.0.0.1:49374"));
            assertThat(contents[9], is("2"));
            assertThat(contents[10], is(Integer.toString(ProxyProtocolV2Data.Tlv.PP2_TYPE_UNIQUE_ID)));
            assertThat(contents[11], is("Hello world"));
            assertThat(contents[12], is("" + 0xE0));
            assertThat(contents[13], is("abc"));
        }

        server.stop();
    }

    private ConnectionListener v1Listener(String proxyHeader) {
        return ConnectionListener.createWriteOnConnect(proxyHeader.getBytes(StandardCharsets.UTF_8));
    }

    private ConnectionListener v2Listener(int[] proxyHeader) {
        byte[] proxyHeaderBytes = new byte[proxyHeader.length];
        for (int i = 0; i < proxyHeader.length; i++) {
            proxyHeaderBytes[i] = (byte) proxyHeader[i];
        }
        return ConnectionListener.createWriteOnConnect(proxyHeaderBytes);
    }
}
