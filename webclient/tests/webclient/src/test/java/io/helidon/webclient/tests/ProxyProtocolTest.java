package io.helidon.webclient.tests;

import io.helidon.http.Status;
import io.helidon.webclient.api.ConnectionInitializer;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProxyProtocolTest {
    @Test
    public void v1Tcp4Support() {
        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                final var data = req.proxyProtocolData().get();
                final var sb = new StringBuilder();
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

        final var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionInitializer(v1Initializer("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            final var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv4.name()));
            assertThat(contents[2], is("192.168.0.1"));
            assertThat(contents[3], is("192.168.0.11"));
            assertThat(contents[4], is("56324"));
            assertThat(contents[5], is("443"));
        }
    }

    @Test
    public void v1Tcp6Support() {
        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                final var data = req.proxyProtocolData().get();
                final var sb = new StringBuilder();
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

        final var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionInitializer(v1Initializer("PROXY TCP6 0000:0000:0000:0000:0000:0000:0000:0000 FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF 56324 443\r\n"))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            final var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv6.name()));
            assertThat(contents[2], is("0000:0000:0000:0000:0000:0000:0000:0000"));
            assertThat(contents[3], is("FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF"));
            assertThat(contents[4], is("56324"));
            assertThat(contents[5], is("443"));
        }
    }

    @Test
    public void v2Ipv4Support() {
        final var server = WebServer.builder()
            .enableProxyProtocol(true)
            .port(0)
            .routing(r -> r.get("/test", (req, res) -> {
                final var data = req.proxyProtocolData().get();
                final var data2 = (ProxyProtocolV2Data) data;
                final var sb = new StringBuilder();
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

        final var client = WebClient.builder()
            .servicesDiscoverServices(false)
            .baseUri(URI.create("http://127.0.0.1:" + server.port()))
            .connectionInitializer(v2Initializer(new int[] {
                0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A, // header
                0x21, // version 1, PROXY command
                0x11, // AF_INET, STREAM
                0x00, 0x00, // 0x0000 bytes remaining,
                0xC0, 0xA8, 0x00, 0x01, // source IPv4 address, 192.168.0.1
                0x7F, 0x00, 0x00, 0x01, // destination IPv4 address, 127.0.0.1
                0xDE, 0xAD, // source IP address, 57005
                0xC0, 0xDE, // dest IP address, 49374
            }))
            .build();
        try (var response = client.get().path("/test").request()) {
            assertThat(response.status(), is(Status.OK_200));
            final var contents = response.entity().as(String.class).split("\\n");
            assertThat(contents[0], is(ProxyProtocolData.Protocol.TCP.name()));
            assertThat(contents[1], is(ProxyProtocolData.Family.IPv4.name()));
            assertThat(contents[2], is("192.168.0.1"));
            assertThat(contents[3], is("127.0.0.1"));
            assertThat(contents[4], is("57005"));
            assertThat(contents[5], is("49374"));
        }
    }

    private ConnectionInitializer v1Initializer(final String proxyHeader) {
        return new ConnectionInitializer() {
            @Override
            public void initializeConnectedSocket(final ConnectedSocket socket) throws IOException {
                socket.socket().getOutputStream().write(proxyHeader.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void initializeConnectedSocket(final ConnectedSocketChannel socket) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    private ConnectionInitializer v2Initializer(final int[] proxyHeader) {
        final byte[] proxyHeaderBytes = new byte[proxyHeader.length];
        for (int i = 0; i < proxyHeader.length; i++) {
            proxyHeaderBytes[i] = (byte) proxyHeader[i];
        }
        return new ConnectionInitializer() {
            @Override
            public void initializeConnectedSocket(final ConnectedSocket socket) throws IOException {
                socket.socket().getOutputStream().write(proxyHeaderBytes);
            }

            @Override
            public void initializeConnectedSocket(final ConnectedSocketChannel socket) {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }
}
