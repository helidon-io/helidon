/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.net.ssl.SSLContext;

import io.helidon.common.configurable.Resource;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.pki.KeyConfig;
import io.helidon.grpc.core.ContextKeys;
import io.helidon.grpc.core.GrpcTlsDescriptor;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.PriorityBag;

import io.grpc.BindableService;
import io.grpc.HandlerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.eclipse.microprofile.health.HealthCheck;

import static java.lang.String.format;

/**
 * A gRPC Server implementation.
 */
public class GrpcServerImpl implements GrpcServer {

    private CompletableFuture<GrpcServer> startFuture = new CompletableFuture<>();

    private CompletableFuture<GrpcServer> shutdownFuture = new CompletableFuture<>();

    /**
     * The {@link Logger} to use.
     */
    private static final Logger LOGGER = Logger.getLogger(GrpcServerImpl.class.getName());

    /**
     * Configuration values.
     */
    private GrpcServerConfiguration config;

    /**
     * The TCP-based gRPC server.
     */
    private Server server;

    /**
     * The in-process gRPC server.
     */
    private Server inProcessServer;

    /**
     * The health status manager.
     */
    private HealthServiceImpl healthService = HealthServiceImpl.create();

    /**
     * The {@link HandlerRegistry} to register services.
     */
    private final MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();

    /**
     * The map of service class name to {@link ServerServiceDefinition}.
     */
    private Map<String, ServerServiceDefinition> mapServices = new ConcurrentHashMap<>();

    /**
     * The map of service names to {@link io.helidon.grpc.server.ServiceDescriptor ServiceDescriptors}.
     */
    private Map<String, ServiceDescriptor> services = new ConcurrentHashMap<>();

    private final Context context;

    // ---- constructors ----------------------------------------------------

    /**
     * Create a {@link GrpcServerImpl}.
     *
     * @param config the configuration for this server
     */
    private GrpcServerImpl(GrpcServerConfiguration config) {
        this.config = config;
        this.context = config.context();

    }

    /**
     * Create a {@link GrpcServerImpl} with the specified configuration.
     *
     * @param config the configuration for this server
     * @return a {@link GrpcServerImpl} with the specified configuration
     */
    static GrpcServerImpl create(GrpcServerConfiguration config) {
        return new GrpcServerImpl(config);
    }

    // ---- GrpcServer interface --------------------------------------------

    @Override
    public CompletionStage<GrpcServer> start() {
        String sName = config.name();
        int port = config.port();
        boolean tls = false;
        GrpcTlsDescriptor tlsConfig = config.tlsConfig();
        SslContext sslContext = null;

        try {
            if (tlsConfig != null) {
                if (tlsConfig.isJdkSSL()) {
                    SSLContext sslCtx = SSLContextBuilder.create(KeyConfig.pemBuilder()
                                                                         .key(tlsConfig.tlsKey())
                                                                         .certChain(tlsConfig.tlsCert())
                                                                         .build()).build();
                    sslContext = new JdkSslContext(sslCtx, false, ClientAuth.NONE);

                } else {
                    sslContext = sslContextBuilder(tlsConfig).build();
                }
            }

            NettyServerBuilder builder = sslContext == null
                    ? NettyServerBuilder.forPort(port)
                    : NettyServerBuilder.forPort(port).sslContext(sslContext);

            HandlerRegistry handlerRegistry = this.handlerRegistry;

            server = configureNetty(builder)
                    .directExecutor()
                    .addService(healthService)
                    .addService(ProtoReflectionService.newInstance())
                    .fallbackHandlerRegistry(handlerRegistry)
                    .build()
                    .start();

            inProcessServer = InProcessServerBuilder
                    .forName(sName)
                    .addService(healthService)
                    .fallbackHandlerRegistry(handlerRegistry)
                    .build()
                    .start();

            LOGGER.log(Level.INFO,
                       () -> format("gRPC server [%s]: listening on port %d (TLS=%s)", sName, server.getPort(), tls));

            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            startFuture.complete(this);
        } catch (Throwable e) {
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, format("gRPC server [%s]: failed to start on port %d (TLS=%s)", sName, port, tls), e);
            startFuture.completeExceptionally(e);
        }
        return startFuture;
    }

    @Override
    public CompletionStage<GrpcServer> shutdown() {
        String name = config.name();

        try {
            if (server != null) {
                server.shutdown();
                inProcessServer.shutdown();
                server.awaitTermination();
                inProcessServer.awaitTermination();

                LOGGER.log(Level.INFO, () -> format("gRPC server [%s]: server stopped", name));
                server = null;
                inProcessServer = null;

                shutdownFuture.complete(this);
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, format("gRPC server [%s]: server failed to shut down", name), e);
            shutdownFuture.completeExceptionally(e);
        }

        return shutdownFuture;
    }

    @Override
    public GrpcServerConfiguration configuration() {
        return config;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public CompletionStage<GrpcServer> whenShutdown() {
        return shutdownFuture;
    }

    @Override
    public boolean isRunning() {
        return server != null && !(server.isShutdown() || server.isTerminated());
    }

    @Override
    public int port() {
        return isRunning() ? server.getPort() : -1;
    }

    @Override
    public HealthCheck[] healthChecks() {
        return healthService.healthChecks().toArray(new HealthCheck[0]);
    }

    @Override
    public Map<String, ServiceDescriptor> services() {
        return Collections.unmodifiableMap(services);
    }

    // ---- helper methods --------------------------------------------------

    private NettyServerBuilder configureNetty(NettyServerBuilder builder) {
        int workersCount = config.workers();

        Class<? extends ServerChannel> channelType = null;
        EventLoopGroup boss = null;
        EventLoopGroup workers = null;

        // ToDo: add back native transport support, so the check bellow makes sense
        // boolean useNative = config.useNativeTransport();

        if (channelType == null) {
            LOGGER.log(Level.FINE, () -> "Using NIO transport");
            channelType = NioServerSocketChannel.class;
            boss = new NioEventLoopGroup(1);
            Executor executor = new ThreadPerTaskExecutor(new ContextAwareThreadFactory(NioEventLoopGroup.class));
            workers = workersCount <= 0
                    ? new NioEventLoopGroup(0, executor)
                    : new NioEventLoopGroup(workersCount, executor);
        }

        return builder
                .channelType(channelType)
                .bossEventLoopGroup(boss)
                .workerEventLoopGroup(workers);
    }

    /**
     * Deploy the specified {@link ServiceDescriptor service} to this {@link GrpcServer}.
     *
     * @param serviceDescriptor  the service to deploy
     * @param globalInterceptors the global {@link io.grpc.ServerInterceptor}s to wrap all services with
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public void deploy(ServiceDescriptor serviceDescriptor, PriorityBag<ServerInterceptor> globalInterceptors) {
        Objects.requireNonNull(serviceDescriptor);
        Objects.requireNonNull(globalInterceptors);

        globalInterceptors.add(new ContextAwareServerInterceptor());

        String serverName = config.name();
        BindableService service = serviceDescriptor.bindableService(globalInterceptors);
        ServerServiceDefinition ssd = service.bindService();
        String serviceName = ssd.getServiceDescriptor().getName();

        services.put(serviceDescriptor.name(), serviceDescriptor);
        handlerRegistry.addService(ssd);
        mapServices.put(service.getClass().getName(), ssd);
        healthService.add(serviceName, serviceDescriptor.healthCheck());

        LOGGER.info(() -> format("gRPC server [%s]: registered service [%s]",
                                 serverName, serviceName));

        Iterator<String> methods = ssd.getMethods()
                .stream()
                .map(ServerMethodDefinition::getMethodDescriptor)
                .map(MethodDescriptor::getFullMethodName)
                .sorted()
                .iterator();

        if (methods.hasNext()) {
            LOGGER.info(() -> format("gRPC server [%s]:       with methods [%s]",
                                     serverName,
                                     methods.next()));
        }
        while (methods.hasNext()) {
            LOGGER.info(() -> format("gRPC server [%s]:                    [%s]",
                                     serverName,
                                     methods.next()));
        }
    }

    /**
     * Undeploy the specified {@link BindableService} from this {@link GrpcServerImpl}.
     *
     * @param service the service to undeploy
     * @param sName   the gRPC server name
     * @throws NullPointerException if {@code service} is {@code null}
     */
    public void undeploy(BindableService service, String sName) {
        Objects.requireNonNull(service);

        String serviceClassName = service.getClass().getName();
        ServerServiceDefinition ssd = mapServices.get(serviceClassName);
        if (null == ssd) {
            return;
        }

        handlerRegistry.removeService(ssd);
        mapServices.remove(serviceClassName);

        LOGGER.info(() -> format("gRPC server [%s]: unregistered service [%s]",
                                 sName,
                                 ssd.getServiceDescriptor().getName()));
    }

    /**
     * Obtain an immutable {@link List} of registered {@link ServerServiceDefinition}s.
     *
     * @return an immutable {@link List} of registered {@link ServerServiceDefinition}s
     */
    public List<ServerServiceDefinition> getServices() {
        return Collections.unmodifiableList(handlerRegistry.getServices());
    }

    /**
     * @return a new in-process {@link ManagedChannel} for interacting with
     *         the services managed by this {@link GrpcServerImpl}.
     */
    public ManagedChannel createInProcessChannel() {
        return InProcessChannelBuilder.forName(config.name()).build();
    }

    /**
     * Return an instance of SslContextBuilder from the specified SslConfig.
     *
     * @param tlsConfig the ssl configuration
     * @return an instance of SslContextBuilder
     */
    protected SslContextBuilder sslContextBuilder(GrpcTlsDescriptor tlsConfig) {
        Resource certResource = tlsConfig.tlsCert();
        Resource keyResource = tlsConfig.tlsKey();
        Resource caCertResource = tlsConfig.tlsCaCert();

        if (certResource == null) {
            throw new IllegalStateException("gRPC server is configured to use TLS but cert file is not set");
        }

        if (keyResource == null) {
            throw new IllegalStateException("gRPC server is configured to use TLS but key file is not set");
        }

        X509Certificate[] aX509Certificates;

        if (caCertResource != null) {
            try {
                aX509Certificates = loadX509Cert(caCertResource.stream());
            } catch (Exception e) {
                throw new IllegalStateException("gRPC server is configured to use TLS but failed to load trusted CA files");
            }

        } else {
            aX509Certificates = new X509Certificate[0];
        }

        SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(certResource.stream(), keyResource.stream())
                .sslProvider(SslProvider.OPENSSL);

        if (aX509Certificates.length > 0) {
            sslContextBuilder.trustManager(aX509Certificates)
                    .clientAuth(ClientAuth.REQUIRE);
        } else {
            sslContextBuilder.clientAuth(ClientAuth.OPTIONAL);
        }

        return GrpcSslContexts.configure(sslContextBuilder);
    }

    private static X509Certificate[] loadX509Cert(InputStream in)
            throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate[] certs = new X509Certificate[1];

        certs[0] = (X509Certificate) cf.generateCertificate(in);

        return certs;
    }

    /**
     * A {@link ServerInterceptor} that will set the Helidon {@link io.helidon.common.context.Context}
     * into the gRPC {@link io.grpc.Context}.
     */
    @Priority(InterceptorPriorities.CONTEXT - 1)
    private class ContextAwareServerInterceptor
            implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {

            Context context = Context.create(context());
            io.grpc.Context grpcContext = io.grpc.Context.current().withValue(ContextKeys.HELIDON_CONTEXT, context);
            return io.grpc.Contexts.interceptCall(grpcContext, call, headers, next);
        }
    }

    /**
     * An extension to {@link DefaultThreadFactory} that ensures threads have
     * a {@link io.helidon.common.context.Context} set.
     */
    private class ContextAwareThreadFactory
            extends DefaultThreadFactory {

        private ContextAwareThreadFactory(Class<?> poolType) {
            super(poolType);
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return super.newThread(() -> {
                Context context = Context.create(context());
                Contexts.runInContext(context, runnable);
            });
        }
    }
}
