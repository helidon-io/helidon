/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.grpc.client;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

import io.helidon.common.configurable.Resource;
import io.helidon.grpc.core.GrpcTlsDescriptor;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.TreeMapService;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GrpcChannelsProviderIT {

    private static final String CLIENT_CERT = "ssl/clientCert.pem";
    private static final String CLIENT_KEY = "ssl/clientKey.pem";
    private static final String CA_CERT = "ssl/ca.pem";
    private static final String SERVER_CERT = "ssl/serverCert.pem";
    private static final String SERVER_KEY = "ssl/serverKey.pem";

    // Constants used as flags by helper methods for determining ssl mode.
    private static final int WITH_NO_SSL = 1;
    private static final int WITH_CA_CERT = 2;
    private static final int WITH_CLIENT_KEY = 4;
    private static final int WITH_CLIENT_CERT = 8;

    // The servers for our tests.
    private static GrpcServer grpcServer_noSsl;
    private static GrpcServer grpcServer_1WaySSL;
    private static GrpcServer grpcServer_2WaySSL;

    // The server ports.
    private static int portNoSsl;
    private static int port1WaySSL;
    private static int port2WaySSL;

    // The descriptor for the (test) TreeService.
    private static ClientServiceDescriptor treeMapSvcDesc;

    @BeforeAll
    public static void initGrpcConfig() throws Exception {
        grpcServer_noSsl = startGrpcServer(portNoSsl, false, true);
        portNoSsl = grpcServer_noSsl.port();

        grpcServer_1WaySSL = startGrpcServer(port1WaySSL, true, false);
        port1WaySSL = grpcServer_1WaySSL.port();

        grpcServer_2WaySSL = startGrpcServer(port2WaySSL, true, true);
        port2WaySSL = grpcServer_2WaySSL.port();

        treeMapSvcDesc = ClientServiceDescriptor.builder("TreeMapService", TreeMapService.class)
                .unary("get")
                .build();
    }

    @AfterAll
    public static void cleanup() throws Exception {
        CompletableFuture<?>[] futures =
                Stream.of(grpcServer_noSsl, grpcServer_1WaySSL, grpcServer_2WaySSL)
                        .map(server -> server.shutdown().toCompletableFuture())
                        .toArray(CompletableFuture<?>[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
    }

    /**
     * Start a gRPC server listening on the specified port and with ssl enabled (if sslEnabled is true).
     *
     * @param nPort      The server port where the server will listen.
     * @param sslEnabled true if ssl enabled.
     * @param mutual     if true then 2 way (mutual) or just one way ssl.
     * @return A reference to a {@link io.helidon.grpc.server.GrpcServer}.
     */
    private static GrpcServer startGrpcServer(int nPort, boolean sslEnabled, boolean mutual) throws Exception {
        Resource tlsCert = Resource.create(SERVER_CERT);
        Resource tlsKey = Resource.create(SERVER_KEY);
        Resource tlsCaCert = Resource.create(CA_CERT);

        GrpcTlsDescriptor sslConfig = null;
        String name = "grpc.server";
        if (!sslEnabled) {
            name = name + 1;
        } else if (mutual) {
            name = name + 2;
            sslConfig = GrpcTlsDescriptor.builder()
                    .jdkSSL(false)
                    .tlsCert(tlsCert)
                    .tlsKey(tlsKey)
                    .tlsCaCert(tlsCaCert)
                    .build();
        } else {
            name = name + 3;
            sslConfig = GrpcTlsDescriptor.builder()
                    .jdkSSL(false)
                    .tlsCert(tlsCert)
                    .tlsKey(tlsKey)
                    .build();
        }
        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                .register(new TreeMapService())
                .build();

        GrpcServerConfiguration.Builder bldr = GrpcServerConfiguration.builder().name(name).port(nPort);
        if (sslEnabled) {
            bldr.tlsConfig(sslConfig);
        }

        return GrpcServer.create(bldr.build(), routing)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

    }

    @Test
    public void shouldConnectWithoutAnyClientCertsToNonSslServer() throws Throwable {
        assertThat(invokeUnary(portNoSsl, WITH_NO_SSL), equalTo(TreeMapService.BILBO));
    }

    @Test()
    public void shouldNotConnectWithoutCaCertTo1WaySslServer() {
        StatusRuntimeException sre = assertThrows(StatusRuntimeException.class, () -> invokeUnary(port1WaySSL, WITH_CLIENT_CERT + WITH_CLIENT_KEY));
        assertThat(sre.getCause().getClass(), equalTo(javax.net.ssl.SSLHandshakeException.class));
    }

    @Test()
    public void shouldConnectWithOnlyCertTo1WaySslServer() throws Throwable {
        assertThat(invokeUnary(port1WaySSL, WITH_CA_CERT), equalTo(TreeMapService.BILBO));
    }

    @Test()
    public void shouldConnectWithClientKeyTo1WaySslServer() throws Throwable {
        assertThat(invokeUnary(port1WaySSL, WITH_CA_CERT + WITH_CLIENT_KEY), equalTo(TreeMapService.BILBO));
    }

    @Test()
    public void shouldConnectWithClientKeyAndClientCertTo1WaySslServer() throws Throwable {
        assertThat(
                invokeUnary(port1WaySSL, WITH_CA_CERT + WITH_CLIENT_KEY + WITH_CLIENT_CERT),
                equalTo(TreeMapService.BILBO)
        );
    }

    @Test
    public void shouldNotConnectWithoutCaCertTo2WaySslServer() {
        StatusRuntimeException sre = assertThrows(StatusRuntimeException.class, () -> invokeUnary(port2WaySSL, WITH_CLIENT_CERT + WITH_CLIENT_KEY));
        assertThat(sre.getCause().getClass(), equalTo(javax.net.ssl.SSLHandshakeException.class));
    }

    @Test
    public void shouldNotConnectWithoutClientCertTo2WaySslServer() {
        StatusRuntimeException sre = assertThrows(StatusRuntimeException.class, () -> invokeUnary(port2WaySSL, WITH_CA_CERT + WITH_CLIENT_KEY));
        assertThat(sre.getCause().getClass(), equalTo(javax.net.ssl.SSLHandshakeException.class));
    }

    @Test
    public void shouldNotConnectWithoutClientKeyTo2WaySslServer() {
        StatusRuntimeException sre = assertThrows(StatusRuntimeException.class, () -> invokeUnary(port2WaySSL, WITH_CA_CERT + WITH_CLIENT_CERT));
        assertThat(sre.getCause().getClass(), equalTo(javax.net.ssl.SSLHandshakeException.class));
    }

    @Test
    public void shouldConnectWithCaCertAndClientCertAndClientKeyTo2WaySslServer() throws Throwable {
        assertThat(
                invokeUnary(port2WaySSL, WITH_CA_CERT + WITH_CLIENT_CERT + WITH_CLIENT_KEY),
                equalTo(TreeMapService.BILBO)
        );
    }

    @Test
    public void shouldUseTarget() throws Exception {
        FakeNameResolverFactory factory = new FakeNameResolverFactory(portNoSsl);
        String channelKey = "ChannelKey";
        GrpcChannelDescriptor.Builder builder = GrpcChannelDescriptor
                .builder()
                .target("foo://bar.com")
                .nameResolverFactory(factory);

        GrpcChannelsProvider provider = GrpcChannelsProvider.builder()
                .channel(channelKey, builder.build())
                .build();

        GrpcServiceClient client = GrpcServiceClient.create(provider.channel(channelKey), treeMapSvcDesc);
        Object result = client.blockingUnary("get", 1);
        assertThat(result, equalTo(TreeMapService.BILBO));
    }

    // A helper method to invoke a unary method. Calls createClient().
    private Object invokeUnary(int serverPort, int mode) throws Throwable {
        GrpcServiceClient client = createClient(serverPort, mode);
        return client.blockingUnary("get", 1);
    }

    /**
     * A helper method that creates a {@link io.helidon.grpc.client.GrpcServiceClient} that will connect
     * to a server at the specified port and with ssl enabled (if mode > 0).
     *
     * @param serverPort The server port.
     * @param sslMode    An int that is a bit wise 'or' of NO_SSL, WITH_CA_CERT, WITH_CLIENT_KEY and WITH_CLIENT_CERT. Note that
     *                   if 'NO_SSL' is set then the rest of the flags are ignored.
     * @return An instance of {@link io.helidon.grpc.client.GrpcServiceClient}.
     * @throws SSLException If mode > 0 and any of the SSL artifacts cannot be obtained.
     */
    private GrpcServiceClient createClient(int serverPort, int sslMode) throws SSLException {
        Resource tlsCaCert = Resource.create(CA_CERT);
        Resource tlsClientCert = Resource.create(CLIENT_CERT);
        Resource tlsClientKey = Resource.create(CLIENT_KEY);

        GrpcChannelDescriptor.Builder chBldr = GrpcChannelDescriptor.builder();
        chBldr.port(serverPort);

        if ((sslMode & WITH_NO_SSL) == 0) {
            // SSL enabled.
            GrpcTlsDescriptor.Builder sslBldr = GrpcTlsDescriptor.builder();
            if ((sslMode & WITH_CA_CERT) > 0) {
                sslBldr.tlsCaCert(tlsCaCert);
            }
            if ((sslMode & WITH_CLIENT_KEY) > 0) {
                sslBldr.tlsKey(tlsClientKey);
            }
            if ((sslMode & WITH_CLIENT_CERT) > 0) {
                sslBldr.tlsCert(tlsClientCert);
            }

            chBldr.sslDescriptor(sslBldr.build());
        }

        String channelKey = "ChannelKey";
        GrpcChannelsProvider grpcClientCfg = GrpcChannelsProvider.builder()
                .channel(channelKey, chBldr.build())
                .build();

        return GrpcServiceClient.create(grpcClientCfg.channel(channelKey), treeMapSvcDesc);
    }


    private static class FakeNameResolverFactory extends NameResolver.Factory {
        private final int port;
        private URI targetUri;
        private NameResolver.Args args;

        public FakeNameResolverFactory(int port) {
            this.port = port;
        }

        @Override
        public String getDefaultScheme() {
            return "directaddress";
        }

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            this.targetUri = targetUri;
            this.args = args;
            return new FakeNameResolver(port);
        }

        public URI getTargetUri() {
            return targetUri;
        }

        public NameResolver.Args getArgs() {
            return args;
        }
    }


    private static class FakeNameResolver extends NameResolver {
        private final int port;

        public FakeNameResolver(int port) {
            this.port = port;
        }

        @Override
        public void start(Listener2 listener) {
            SocketAddress address = new InetSocketAddress("localhost", port);
            EquivalentAddressGroup group = new EquivalentAddressGroup(Collections.singletonList(address));
            List<EquivalentAddressGroup> addresses = Collections.singletonList(group);
            listener.onResult(ResolutionResult.newBuilder().setAddresses(addresses).build());
        }

        @Override
        public void refresh() {
        }

        @Override
        public String getServiceAuthority() {
            return "";
        }

        @Override
        public void shutdown() {
        }
    }
}
