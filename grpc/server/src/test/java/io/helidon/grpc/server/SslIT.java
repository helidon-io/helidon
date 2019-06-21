/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import java.io.File;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.core.GrpcSslDescriptor;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;

import com.oracle.bedrock.runtime.LocalPlatform;
import com.oracle.bedrock.runtime.network.AvailablePortIterator;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import org.junit.AfterClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import services.EchoService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for gRPC server with SSL connections
 */
public class SslIT {

    // ----- data members ---------------------------------------------------

    /**
     * The {@link java.util.logging.Logger} to use for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(SslIT.class.getName());

    /**
     * The Helidon {@link GrpcServer} being tested.
     */
    private static GrpcServer grpcServer_1WaySSL;

    /**
     * The Helidon {@link GrpcServer} being tested.
     */
    private static GrpcServer grpcServer_2WaySSL;

    /**
     * The Helidon {@link GrpcServer} being tested.
     */
    private static GrpcServer grpcServer_2WaySSLConfig;

    /**
     * Port used for 1waySSL
     */
    private static int port1WaySSL;

    /**
     * Port used for 2waySSL
     */
    private static int port2WaySSL;

    /**
     * Port used for 2waySSL using config-ssl.yaml
     */
    private static int port2WaySSLConfig;

    private static final String CLIENT_CERT = "clientCert.pem";
    private static final String CLIENT_KEY  = "clientKey.pem";
    private static final String CA_CERT     = "ca.pem";
    private static final String SERVER_CERT = "serverCert.pem";
    private static final String SERVER_KEY  = "serverKey.pem";

    private static String tlsCert;
    private static String tlsKey;
    private static String tlsCaCert;
    private static String tlsClientKey;
    private static String tlsClientCert;

    private static String filePath;

    // ----- test lifecycle -------------------------------------------------

    @BeforeAll
    public static void setup() throws Exception {
        LogManager.getLogManager().readConfiguration(SslIT.class.getResourceAsStream("/logging.properties"));
        File resourcesDirectory = new File("src/test/resources/ssl");
        filePath = resourcesDirectory.getAbsolutePath();
        tlsCert = getFile(SERVER_CERT);
        tlsKey = getFile(SERVER_KEY);
        tlsCaCert = getFile(CA_CERT);
        tlsClientCert = getFile(CLIENT_CERT);
        tlsClientKey = getFile(CLIENT_KEY);

        AvailablePortIterator ports = LocalPlatform.get().getAvailablePorts();

        port1WaySSL = ports.next();
        port2WaySSL = ports.next();
        port2WaySSLConfig = ports.next();

        grpcServer_1WaySSL = startGrpcServer(port1WaySSL, false /*mutual*/, false /*useConfig*/);
        grpcServer_2WaySSL = startGrpcServer(port2WaySSL, true /*mutual*/, false /*useConfig*/);
        grpcServer_2WaySSLConfig = startGrpcServer(port2WaySSLConfig, true/*mutual*/, true /*useConfig*/);
    }

    @AfterClass
    public static void cleanup() throws Exception
    {
        CompletableFuture<?>[] futures =
                         Stream.of(grpcServer_1WaySSL, grpcServer_2WaySSL, grpcServer_2WaySSLConfig)
                        .map(server -> server.shutdown().toCompletableFuture())
                        .toArray(CompletableFuture<?>[]::new);

        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
    }

    // ----- test methods ---------------------------------------------------

    @Test
    public void shouldConnectWithoutClientCertsFor1Way() throws Exception {
        // client do not have to provide certs for 1way ssl
        SslContext sslContext = clientSslContext(tlsCaCert, null, null);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_1WaySSL.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service suggestion
        Echo.EchoResponse response = EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldNotConnectWithoutCAFor1Way() throws Exception {
        // client do not have to provide certs for 1way ssl
        SslContext sslContext = clientSslContext(null, null, null);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_1WaySSL.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service should throw
        Assertions.assertThrows(StatusRuntimeException.class,
                                ()->EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldConnectWithClientCertsFor2Way() throws Exception {
        SslContext sslContext = clientSslContext(tlsCaCert, tlsClientCert, tlsClientKey);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_2WaySSL.port())
                    .negotiationType(NegotiationType.TLS)
                    .sslContext(sslContext)
                    .build();

        // call the gRPC Echo service
        Echo.EchoResponse response = EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldNotConnectWithoutCAFor2Way() throws Exception {
        SslContext sslContext = clientSslContext(null, tlsClientCert, tlsClientKey);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_2WaySSL.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service should throw
        Assertions.assertThrows(StatusRuntimeException.class,
                                ()->EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldNotConnectWithoutClientCertFor2Way() throws Exception {
        SslContext sslContext = clientSslContext(tlsCaCert, null, tlsClientKey);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_2WaySSL.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service should throw
        Assertions.assertThrows(StatusRuntimeException.class,
                                ()->EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldConnectWithClientCertsFor2WayUseConfig() throws Exception{
        SslContext sslContext = clientSslContext(tlsCaCert, tlsClientCert, tlsClientKey);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_2WaySSLConfig.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service
        Echo.EchoResponse response = EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void shouldNotConnectWithoutClientCertFor2WayUseConfig() throws Exception {
        SslContext sslContext = clientSslContext(tlsCaCert, null, tlsClientKey);

        Channel channel = NettyChannelBuilder.forAddress("localhost", grpcServer_2WaySSLConfig.port())
                .negotiationType(NegotiationType.TLS)
                .sslContext(sslContext)
                .build();

        // call the gRPC Echo service should throw
        Assertions.assertThrows(StatusRuntimeException.class,
                                ()->EchoServiceGrpc.newBlockingStub(channel).echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        ((ManagedChannel) channel).shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ----- helper methods -------------------------------------------------

    private static SslContext clientSslContext(String trustCertCollectionFilePath,
                                               String clientCertChainFilePath,
                                               String clientPrivateKeyFilePath) throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forClient();
        if (trustCertCollectionFilePath != null) {
            builder.trustManager(new File(trustCertCollectionFilePath));
        }

        if (clientCertChainFilePath != null && clientPrivateKeyFilePath != null) {
            builder.keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath));
        }
        return builder.build();
    }

    /**
     * Start the gRPC Server listening on the specified nPort.
     *
     * @throws Exception in case of an error
     */
    private static GrpcServer startGrpcServer(int nPort, boolean mutual, boolean useConfig ) throws Exception {
        GrpcSslDescriptor sslConfig;
        String name = "grpc.server";
        if (useConfig) {
            name = name + 1;
            Config config = Config.builder().sources(ConfigSources.classpath("config-ssl.conf")).build();
            sslConfig = config.get("grpcserver.ssl").as(GrpcSslDescriptor::create).get();
        } else if (mutual) {
            name = name + 2;
             sslConfig = GrpcSslDescriptor.builder()
                        .jdkSSL(false)
                        .tlsCert(tlsCert)
                        .tlsKey(tlsKey)
                        .tlsCaCert(tlsCaCert)
                        .build();
        } else {
            name = name + 3;
            sslConfig = GrpcSslDescriptor.builder()
                        .jdkSSL(false)
                        .tlsCert(tlsCert)
                        .tlsKey(tlsKey)
                        .build();
        }
        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                                         .register(new EchoService())
                                         .build();

        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().name(name).port(nPort).sslConfig(sslConfig).build();

        GrpcServer grpcServer ;
        try {
            grpcServer = GrpcServer.create(serverConfig, routing)
                    .start()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

       LOGGER.info("Started gRPC server at: localhost:" + grpcServer.port());

       return grpcServer;
    }

    private static String getFile(String fileName){
        return filePath + "/" + fileName;
    }
}
