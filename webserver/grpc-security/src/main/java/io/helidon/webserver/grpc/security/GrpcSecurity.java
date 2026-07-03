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

package io.helidon.webserver.grpc.security;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.webserver.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.grpc.spi.GrpcServerService;

import io.grpc.Context;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Integration of security into the gRPC server.
 * <p>
 * The interceptor creates a {@link SecurityContext} for each gRPC call. Use
 * {@link #securityDefaults(GrpcSecurityHandler)} to set defaults for all calls, and use the handler factory methods on this
 * class to configure security for services or methods.
 */
@Weight(InterceptorWeights.AUTHENTICATION)
public final class GrpcSecurity implements ServerInterceptor, GrpcServerService, GrpcServiceDescriptor.Configurer {
    static final String TYPE = "security";
    private static final String TOP_LEVEL_CONFIG_KEY = "grpc.grpc-services.security";
    private static final String SERVER_PROTOCOL_CONFIG_KEY = "server.protocols.grpc.grpc-services.security";
    private static final String GRPC_SERVICES_CONFIG_KEY = "grpc-services.security";

    /**
     * Security can accept additional headers to be added to security request.
     */
    public static final Context.Key<Map<String, List<String>>> CONTEXT_ADD_HEADERS =
            Context.key("security.addHeaders");

    /**
     * The security context gRPC context key.
     */
    public static final Context.Key<SecurityContext> SECURITY_CONTEXT =
            Context.key("Helidon.SecurityContext");

    /**
     * The default security handler gRPC context key.
     */
    public static final Context.Key<GrpcSecurityHandler> GRPC_SECURITY_HANDLER =
            Context.key("Helidon.SecurityInterceptor");

    static final Context.Key<GrpcSecurityHandler> GRPC_SERVICE_SECURITY_HANDLER =
            Context.key("Helidon.SecurityServiceInterceptor");

    static final Context.Key<GrpcSecurityHandler> GRPC_METHOD_SECURITY_HANDLER =
            Context.key("Helidon.SecurityMethodInterceptor");

    /**
     * The value used for the key of the security context environment's ABAC request remote address attribute.
     */
    public static final String ABAC_ATTRIBUTE_REMOTE_ADDRESS = "userIp";

    /**
     * The value used for the key of the security context environment's ABAC request remote port attribute.
     */
    public static final String ABAC_ATTRIBUTE_REMOTE_PORT = "userPort";

    /**
     * The value used for the key of the security context environment's ABAC request headers attribute.
     */
    public static final String ABAC_ATTRIBUTE_HEADERS = "metadata";

    /**
     * The value used for the key of the security context environment's ABAC request method descriptor attribute.
     */
    public static final String ABAC_ATTRIBUTE_METHOD = "methodDescriptor";

    private static final System.Logger LOGGER = System.getLogger(GrpcSecurity.class.getName());
    private static final AtomicInteger SECURITY_COUNTER = new AtomicInteger();

    private final Security security;
    private final Config config;
    private final boolean enabled;
    private final GrpcSecurityHandler defaultHandler;
    private final Map<String, ServiceSecurity> services;

    private GrpcSecurity(Security security, Config config) {
        this(security, config, GrpcSecurityHandler.create());
    }

    private GrpcSecurity(Security security, Config config, GrpcSecurityHandler defaultHandler) {
        this.security = Objects.requireNonNull(security);
        this.config = Objects.requireNonNull(config);
        GrpcSecurityConfig parsedConfig = this.config.exists()
                ? GrpcSecurityConfig.create(this.config)
                : GrpcSecurityConfig.create();
        this.enabled = parsedConfig.enabled();
        Config defaultsConfig = this.config.get("defaults");
        this.defaultHandler = defaultsConfig.exists()
                ? GrpcSecurityHandler.create(defaultsConfig, defaultHandler)
                : defaultHandler;

        Map<String, ServiceSecurity> serviceMap = new HashMap<>();
        for (GrpcSecurityServiceConfig serviceConfig : parsedConfig.services()) {
            GrpcSecurityHandler serviceDefaults = serviceConfig.defaults().combine(this.defaultHandler);
            Map<String, GrpcSecurityHandler> methods = new HashMap<>();
            for (GrpcSecurityMethodConfig methodConfig : serviceConfig.methods()) {
                GrpcSecurityHandlerConfig.Builder handlerBuilder = GrpcSecurityHandler.builder();

                if (!methodConfig.rolesAllowed().isEmpty()) {
                    handlerBuilder.rolesAllowed(methodConfig.rolesAllowed());
                }
                methodConfig.authenticator().ifPresent(handlerBuilder::authenticator);
                methodConfig.authorizer().ifPresent(handlerBuilder::authorizer);
                methodConfig.authenticate().ifPresent(handlerBuilder::authenticate);
                methodConfig.authenticationOptional().ifPresent(handlerBuilder::authenticationOptional);
                methodConfig.audit().ifPresent(handlerBuilder::audit);
                methodConfig.authorize().ifPresent(handlerBuilder::authorize);
                methodConfig.auditEventType().ifPresent(handlerBuilder::auditEventType);
                methodConfig.auditMessageFormat().ifPresent(handlerBuilder::auditMessageFormat);
                methodConfig.customObjects().ifPresent(handlerBuilder::customObjects);
                methodConfig.config().ifPresent(handlerBuilder::config);

                methods.put(methodConfig.name(), handlerBuilder.build().combine(serviceDefaults));
            }
            serviceMap.put(serviceConfig.name(), new ServiceSecurity(serviceDefaults, Map.copyOf(methods)));
        }
        this.services = Map.copyOf(serviceMap);
    }

    /**
     * Create a new gRPC security interceptor.
     *
     * @param security initialized security
     * @return gRPC security
     */
    public static GrpcSecurity create(Security security) {
        return new GrpcSecurity(security, Config.empty(), GrpcSecurityHandler.create());
    }

    /**
     * Create a new gRPC security interceptor from configuration.
     * <p>
     * The configuration instance may be the root config or a {@code grpc-services.security} node. The security instance
     * is created from the root {@code security} node.
     *
     * @param config configuration
     * @return gRPC security
     */
    public static GrpcSecurity create(Config config) {
        Objects.requireNonNull(config);
        return create(Security.create(config.root().get("security")), config);
    }

    /**
     * Create a new gRPC security interceptor from security and configuration.
     * <p>
     * The configuration instance may be the root config or a {@code grpc-services.security} node.
     *
     * @param security initialized security
     * @param config configuration
     * @return gRPC security
     */
    public static GrpcSecurity create(Security security, Config config) {
        Objects.requireNonNull(config);

        Config grpcSecurityConfig = config;
        String key = config.key().toString();
        if (TOP_LEVEL_CONFIG_KEY.equals(key)
                || SERVER_PROTOCOL_CONFIG_KEY.equals(key)
                || GRPC_SERVICES_CONFIG_KEY.equals(key)
                || key.endsWith("." + GRPC_SERVICES_CONFIG_KEY)) {
            grpcSecurityConfig = config;
        } else if (config.key().isRoot()) {
            Config protocolConfig = config.get(SERVER_PROTOCOL_CONFIG_KEY);
            Config topLevelConfig = config.get(TOP_LEVEL_CONFIG_KEY);
            grpcSecurityConfig = protocolConfig.exists()
                    ? protocolConfig
                    : (topLevelConfig.exists() ? topLevelConfig : Config.empty());
        } else {
            Config childConfig = config.get(GRPC_SERVICES_CONFIG_KEY);
            if (childConfig.exists()) {
                grpcSecurityConfig = childConfig;
            }
        }
        return new GrpcSecurity(security, grpcSecurityConfig);
    }

    /**
     * Secure access using authentication and authorization.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler secure() {
        return GrpcSecurityHandler.create().authenticate().authorize();
    }

    /**
     * Authenticate a request.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler authenticate() {
        return GrpcSecurityHandler.create().authenticate();
    }

    /**
     * Audit a request.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler audit() {
        return GrpcSecurityHandler.create().audit();
    }

    /**
     * Use a named authenticator.
     *
     * @param explicitAuthenticator authenticator name
     * @return security handler
     */
    public static GrpcSecurityHandler authenticator(String explicitAuthenticator) {
        return GrpcSecurityHandler.create().authenticate().authenticator(explicitAuthenticator);
    }

    /**
     * Use a named authorizer.
     *
     * @param explicitAuthorizer authorizer name
     * @return security handler
     */
    public static GrpcSecurityHandler authorizer(String explicitAuthorizer) {
        return GrpcSecurityHandler.create().authenticate().authorize().authorizer(explicitAuthorizer);
    }

    /**
     * Require at least one of the specified roles.
     *
     * @param roles allowed roles
     * @return security handler
     */
    public static GrpcSecurityHandler rolesAllowed(String... roles) {
        return GrpcSecurityHandler.create().rolesAllowed(roles);
    }

    /**
     * Authenticate if possible and continue as anonymous on authentication failure.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler allowAnonymous() {
        return GrpcSecurityHandler.create().authenticate().authenticationOptional();
    }

    /**
     * Authorize a request.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler authorize() {
        return GrpcSecurityHandler.create().authorize();
    }

    /**
     * Return a default handler to customize.
     *
     * @return security handler
     */
    public static GrpcSecurityHandler enforce() {
        return GrpcSecurityHandler.create();
    }

    /**
     * Create a new gRPC security instance using the provided handler as the least-significant default.
     *
     * @param defaultHandler default security handler
     * @return gRPC security
     */
    public GrpcSecurity securityDefaults(GrpcSecurityHandler defaultHandler) {
        Objects.requireNonNull(defaultHandler, "Default security handler must not be null");
        return new GrpcSecurity(security, config, defaultHandler);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public WeightedBag<ServerInterceptor> interceptors() {
        WeightedBag<ServerInterceptor> interceptors = WeightedBag.create();
        if (enabled) {
            interceptors.add(this, InterceptorWeights.AUTHENTICATION);
        }
        return interceptors;
    }

    @Override
    public void configure(GrpcServiceDescriptor.Rules rules) {
        if (enabled) {
            rules.intercept(InterceptorWeights.AUTHENTICATION, this);
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        if (!enabled) {
            return next.startCall(call, headers);
        }

        Context context = registerContext(call, headers);

        try {
            GrpcSecurityHandler handler = securityHandler(context, call.getMethodDescriptor());

            ServerCall.Listener<ReqT> listener = context.call(() -> handler.handleSecurity(call, headers, next));
            return new ContextualizedServerCallListener<>(listener, context);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected exception during security processing", throwable);
            call.close(Status.INTERNAL, new Metadata());
            return new GrpcSecurityHandler.EmptyListener<>();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <ReqT, RespT> Context registerContext(ServerCall<ReqT, RespT> call, Metadata headers) {
        if (SECURITY_CONTEXT.get() != null) {
            return Context.current();
        }

        SocketAddress remoteSocket = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        String address;
        int port = -1;

        if (remoteSocket instanceof InetSocketAddress inetSocketAddress) {
            address = inetSocketAddress.getHostString();
            port = inetSocketAddress.getPort();
        } else {
            address = String.valueOf(remoteSocket);
        }

        Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String name : headers.keys()) {
            if (name.endsWith("-bin")) {
                continue;
            }
            Metadata.Key key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
            Iterable<Object> iterable = headers.getAll(key);
            List<String> values = new ArrayList<>();

            if (iterable != null) {
                for (Object value : iterable) {
                    values.add(String.valueOf(value));
                }
            }

            headerMap.put(name, values);
        }

        Map<String, List<String>> extraHeaders = CONTEXT_ADD_HEADERS.get();
        if (extraHeaders != null) {
            headerMap.putAll(extraHeaders);
        }

        MethodDescriptor<ReqT, RespT> methodDescriptor = call.getMethodDescriptor();
        String methodName = methodDescriptor.getFullMethodName();

        SecurityEnvironment env = security.environmentBuilder()
                .path(methodName)
                .method(methodName)
                .headers(headerMap)
                .addAttribute(ABAC_ATTRIBUTE_REMOTE_ADDRESS, address)
                .addAttribute(ABAC_ATTRIBUTE_REMOTE_PORT, port)
                .addAttribute(ABAC_ATTRIBUTE_HEADERS, headers)
                .addAttribute(ABAC_ATTRIBUTE_METHOD, methodDescriptor)
                .transport("grpc")
                .build();

        SecurityContext securityContext = security.contextBuilder(String.valueOf(SECURITY_COUNTER.incrementAndGet()))
                .env(env)
                .endpointConfig(EndpointConfig.builder().build())
                .build();

        io.helidon.common.context.Contexts.context().ifPresent(ctx -> ctx.register(securityContext));

        return Context.current().withValue(SECURITY_CONTEXT, securityContext);
    }

    GrpcSecurityHandler securityHandler(Context context, MethodDescriptor<?, ?> methodDescriptor) {
        GrpcSecurityHandler handler = configuredHandler(methodDescriptor);
        GrpcSecurityHandler programmaticHandler = GRPC_SECURITY_HANDLER.get(context);

        GrpcSecurityHandler serviceHandler = GRPC_SERVICE_SECURITY_HANDLER.get(context);
        if (serviceHandler != null) {
            programmaticHandler = serviceHandler.combine(programmaticHandler == null
                                                                 ? GrpcSecurityHandler.create()
                                                                 : programmaticHandler);
        }

        GrpcSecurityHandler methodHandler = GRPC_METHOD_SECURITY_HANDLER.get(context);
        if (methodHandler != null) {
            programmaticHandler = methodHandler.combine(programmaticHandler == null
                                                                ? GrpcSecurityHandler.create()
                                                                : programmaticHandler);
        }

        return programmaticHandler == null ? handler : handler.combine(programmaticHandler);
    }

    Security security() {
        return security;
    }

    GrpcSecurityHandler defaultHandler() {
        return defaultHandler;
    }

    private GrpcSecurityHandler configuredHandler(MethodDescriptor<?, ?> methodDescriptor) {
        if (services.isEmpty()) {
            return defaultHandler;
        }

        String fullMethodName = methodDescriptor.getFullMethodName();
        int slash = fullMethodName.lastIndexOf('/');
        if (slash < 1) {
            return defaultHandler;
        }
        String serviceName = fullMethodName.substring(0, slash);
        String methodName = fullMethodName.substring(slash + 1);
        ServiceSecurity serviceSecurity = services.get(serviceName);

        if (serviceSecurity == null) {
            int lastDot = serviceName.lastIndexOf('.');
            if (lastDot >= 0) {
                serviceSecurity = services.get(serviceName.substring(lastDot + 1));
            }
        }

        return serviceSecurity == null ? defaultHandler : serviceSecurity.handler(methodName);
    }

    private record ServiceSecurity(GrpcSecurityHandler defaults, Map<String, GrpcSecurityHandler> methods) {
        private GrpcSecurityHandler handler(String methodName) {
            return methods.getOrDefault(methodName, defaults);
        }
    }

    private static class ContextualizedServerCallListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
        private final Context context;

        private ContextualizedServerCallListener(ServerCall.Listener<ReqT> delegate, Context context) {
            super(delegate);
            this.context = context;
        }

        @Override
        public void onMessage(ReqT message) {
            Context previous = context.attach();
            try {
                super.onMessage(message);
            } finally {
                context.detach(previous);
            }
        }

        @Override
        public void onHalfClose() {
            Context previous = context.attach();
            try {
                super.onHalfClose();
            } finally {
                context.detach(previous);
            }
        }

        @Override
        public void onCancel() {
            Context previous = context.attach();
            try {
                super.onCancel();
            } finally {
                context.detach(previous);
            }
        }

        @Override
        public void onComplete() {
            Context previous = context.attach();
            try {
                super.onComplete();
            } finally {
                context.detach(previous);
            }
        }

        @Override
        public void onReady() {
            Context previous = context.attach();
            try {
                super.onReady();
            } finally {
                context.detach(previous);
            }
        }
    }
}
