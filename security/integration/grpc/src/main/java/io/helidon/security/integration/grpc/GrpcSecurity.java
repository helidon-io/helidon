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

package io.helidon.security.integration.grpc;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;

import io.grpc.Context;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.contrib.grpc.OpenTracingContextKey;

/**
 * Integration of security into the gRPC Server.
 * <p>
 * Methods that start with "from" are to register GrpcSecurity with {@link io.helidon.grpc.server.GrpcServer}
 * - to create {@link SecurityContext} for requests:
 * <ul>
 * <li>{@link #create(Security)}</li>
 * <li>{@link #create(Config)}</li>
 * <li>{@link #create(Security, Config)}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * // gRPC server routing builder - this is our integration point
 * {@link GrpcRouting} routing = GrpcRouting.builder()
 * // register GrpcSecurity to add the security ServerInterceptor
 * .intercept({@link GrpcSecurity}.{@link
 * GrpcSecurity#create(Security) create(security)})
 * </pre>
 * <p>
 * Other methods are to create security enforcement points (gates) for specific services.
 * These methods are starting points that provide an instance of {@link GrpcSecurityHandler} that has finer grained
 * methods to control the gate behavior. <br>
 * Note that if any gate is configured, auditing will be enabled by default if you want to audit any method, invoke
 * {@link #audit()} to create a gate that will always audit the route.
 * If you want to create a gate and not audit it, use {@link GrpcSecurityHandler#skipAudit()} on the returned instance.
 * <ul>
 * <li>{@link #secure()} - authentication and authorization</li>
 * <li>{@link #rolesAllowed(String...)} - role based access control (implies authentication and authorization)</li>
 * <li>{@link #authenticate()} - authentication only</li>
 * <li>{@link #authorize()} - authorization only</li>
 * <li>{@link #allowAnonymous()} - authentication optional</li>
 * <li>{@link #audit()} - audit all requests</li>
 * <li>{@link #authenticator(String)} - use explicit authenticator (named - as configured in config or through builder)</li>
 * <li>{@link #authorizer(String)} - use explicit authorizer (named - as configured in config or through builder)</li>
 * <li>{@link #enforce()} - use defaults (e.g. no authentication, authorization, audit calls; this also give access to
 * more fine-grained methods of {@link GrpcSecurityHandler}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * // continue from example above...
 * // create a gate for method GET: authenticate all paths under /user and require role "user" for authorization
 * .intercept({@link io.helidon.grpc.server.GrpcService}, GrpcSecurity.{@link GrpcSecurity#rolesAllowed(String...)
 * rolesAllowed("user")})
 * </pre>
 */
// we need to have all fields optional and this is cleaner than checking for null
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Priority(InterceptorPriorities.AUTHENTICATION)
public final class GrpcSecurity
        implements ServerInterceptor, ServiceDescriptor.Configurer {
    private static final Logger LOGGER = Logger.getLogger(GrpcSecurity.class.getName());

    /**
     * Security can accept additional headers to be added to security request.
     * This will be used to obtain multi-value string map (a map of string to list of strings) from context (appropriate
     * to the integration).
     */
    public static final Context.Key<Map> CONTEXT_ADD_HEADERS = Context.key("security.addHeaders");

    /**
     * The SecurityContext gRPC metadata header key.
     */
    public static final Context.Key<SecurityContext> SECURITY_CONTEXT =
            Context.key("Helidon.SecurityContext");

    /**
     * The default security handler gRPC metadata header key.
     */
    public static final Context.Key<GrpcSecurityHandler> GRPC_SECURITY_HANDLER =
            Context.key("Helidon.SecurityInterceptor");

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

    // Security configuration keys
    private static final String KEY_GRPC_CONFIG = "grpc-server";

    private static final AtomicInteger SECURITY_COUNTER = new AtomicInteger();

    private final Security security;
    private final Optional<Config> config;
    private final GrpcSecurityHandler defaultHandler;

    private GrpcSecurity(Security security, Config config) {
        this(security, Optional.ofNullable(config), GrpcSecurityHandler.create());
    }

    private GrpcSecurity(Security security, Optional<Config> config, GrpcSecurityHandler defaultHandler) {
        this.security = security;
        this.config = config.map(cfg -> cfg.get(KEY_GRPC_CONFIG));
        this.defaultHandler = this.config
                .map(cfg -> GrpcSecurityHandler.create(cfg.get("defaults"), defaultHandler))
                .orElse(defaultHandler);
    }

    /**
     * Create a consumer of gRPC routing config to be {@link GrpcRouting.Builder#register(GrpcService)}) registered} with
     * gRPC server routing to process security requests.
     * This method is to be used together with other routing methods to protect gRPC service or methods programmatically.
     * Example:
     * <pre>
     * .intercept(GrpcSecurity.authenticate().rolesAllowed("user"))
     * </pre>
     *
     * @param security initialized security
     * @return routing config consumer
     */
    public static GrpcSecurity create(Security security) {
        return create(security, null);
    }

    /**
     * Create a consumer of gRPC routing config to be {@link GrpcRouting.Builder#register(GrpcService) registered} with
     * gRPC server routing to process security requests.
     * This method configures security and gRPC server integration from a config instance
     *
     * @param config Config instance to load security and gRPC server integration from configuration
     * @return routing config consumer
     */
    public static GrpcSecurity create(Config config) {
        Security security = Security.create(config);
        return create(security, config);
    }

    /**
     * Create a consumer of gRPC routing config to be {@link GrpcRouting.Builder#register(GrpcService) registered} with
     * gRPC server routing to process security requests.
     * This method expects initialized security and creates gRPC server integration from a config instance
     *
     * @param security Security instance to use
     * @param config   Config instance to load security and gRPC server integration from configuration
     * @return routing config consumer
     */
    public static GrpcSecurity create(Security security, Config config) {
        return new GrpcSecurity(security, config);
    }

    /**
     * Secure access using authentication and authorization.
     * Auditing is enabled by default for methods modifying content.
     * When using RBAC (role based access control), just use {@link #rolesAllowed(String...)}.
     * If you use a security provider, that requires additional data, use {@link GrpcSecurityHandler#customObject(Object)}.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled if provider configured</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance configured with authentication and authorization
     */
    public static GrpcSecurityHandler secure() {
        return GrpcSecurityHandler.create().authenticate().authorize();
    }

    /**
     * If called, request will go through authentication process - defaults to false (even if authorize is true).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler authenticate() {
        return GrpcSecurityHandler.create().authenticate();
    }

    /**
     * Whether to audit this request - defaults to false for GET and HEAD methods, true otherwise.
     * Request is audited with event type "request".
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: not modified (default: disabled)</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: enabled for any method this gate is registered on</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler audit() {
        return GrpcSecurityHandler.create().audit();
    }

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link Security}
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler authenticator(String explicitAuthenticator) {
        return GrpcSecurityHandler.create().authenticate().authenticator(explicitAuthenticator);
    }

    /**
     * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     * permitted).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled with explicit provider</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @param explicitAuthorizer name of authorizer as configured in {@link Security}
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler authorizer(String explicitAuthorizer) {
        return GrpcSecurityHandler.create().authenticate().authorize().authorizer(explicitAuthorizer);
    }

    /**
     * An array of allowed roles for this path - must have a security provider supporting roles.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @param roles if subject is any of these roles, allow access
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler rolesAllowed(String... roles) {
        return GrpcSecurityHandler.create().rolesAllowed(roles);
    }

    /**
     * If called, authentication failure will not abort request and will continue as anonymous (defaults to false).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and optional</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler allowAnonymous() {
        return GrpcSecurityHandler.create().authenticate().authenticationOptional();
    }

    /**
     * Enable authorization for this route.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled if provider is present</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler authorize() {
        return GrpcSecurityHandler.create().authorize();
    }

    /**
     * Return a default instance to create a default enforcement point (or modify the result further).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: not modified (default: disabled)</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified</li>
     * </ul>
     *
     * @return {@link GrpcSecurityHandler} instance
     */
    public static GrpcSecurityHandler enforce() {
        return GrpcSecurityHandler.create();
    }

    /**
     * Create a new gRPC security instance using the default handler as base defaults for all handlers used.
     * If handlers are loaded from config, than this is the least significant value.
     *
     * @param defaultHandler if a security handler is configured for a route, it will take its defaults from this handler
     * @return new instance of gRPC security with the handler default
     */
    public GrpcSecurity securityDefaults(GrpcSecurityHandler defaultHandler) {
        Objects.requireNonNull(defaultHandler, "Default security handler must not be null");
        return new GrpcSecurity(security, config, defaultHandler);
    }

    /**
     * If the {@link #config} field is set then modify the {@link ServiceDescriptor.Rules}
     * with any applicable security configuration.
     *
     * @param rules  the {@link ServiceDescriptor.Rules} to modify
     */
    @Override
    public void configure(ServiceDescriptor.Rules rules) {
        config.ifPresent(grpcConfig -> modifyServiceDescriptorConfig(rules, grpcConfig));
    }

    private void modifyServiceDescriptorConfig(ServiceDescriptor.Rules rules, Config grpcConfig) {
        String serviceName = rules.name();

        grpcConfig.get("services")
                .asNodeList()
                .map(list -> findServiceConfig(serviceName, list))
                .ifPresent(cfg -> configureServiceSecurity(rules, cfg));
    }

    private Config findServiceConfig(String serviceName, List<Config> list) {
        return list.stream()
                .filter(cfg -> cfg.get("name").asString().map(serviceName::equals).orElse(false))
                .findFirst()
                .orElse(null);
    }

    private void configureServiceSecurity(ServiceDescriptor.Rules rules, Config grpcServiceConfig) {
        if (grpcServiceConfig.exists()) {
            GrpcSecurityHandler defaults;

            if (grpcServiceConfig.get("defaults").exists()) {
                defaults = GrpcSecurityHandler.create(grpcServiceConfig.get("defaults"), defaultHandler);
            } else {
                defaults = defaultHandler;
            }

            Config methodsConfig = grpcServiceConfig.get("methods");
            if (methodsConfig.exists()) {
                methodsConfig.asNodeList().ifPresent(configs -> {
                    for (Config methodConfig : configs) {
                        String name = methodConfig.get("name")
                                .asString()
                                .orElseThrow(() -> new SecurityException(methodConfig
                                                         .key() + " must contain name key with a method name to "
                                                         + "register to gRPC server security"));

                        rules.intercept(name, GrpcSecurityHandler.create(methodConfig, defaults));
                    }
                });
            } else {
                rules.intercept(defaults);
            }
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        Context context = registerContext(call, headers);

        try {
            GrpcSecurityHandler configuredHandler = GrpcSecurity.GRPC_SECURITY_HANDLER.get(context);
            GrpcSecurityHandler handler = configuredHandler == null ? defaultHandler : configuredHandler;

            ServerCall.Listener<ReqT> listener = context.call(() -> handler.handleSecurity(call, headers, next));

            return new ContextualizedServerCallListener<>(listener, context);
        } catch (Throwable throwable) {
            LOGGER.log(Level.SEVERE, "Unexpected exception during security processing", throwable);
            call.close(Status.INTERNAL, new Metadata());
            return new GrpcSecurityHandler.EmptyListener<>();
        }
    }

    @SuppressWarnings("unchecked")
    <ReqT, RespT> Context registerContext(ServerCall<ReqT, RespT> call, Metadata headers) {
        Context grpcContext;

        if (SECURITY_CONTEXT.get() == null) {
            SocketAddress remoteSocket = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            String address = null;
            int port = -1;

            if (remoteSocket instanceof InetSocketAddress) {
                address = ((InetSocketAddress) remoteSocket).getHostName();
                port = ((InetSocketAddress) remoteSocket).getPort();
            } else {
                address = String.valueOf(remoteSocket);
            }

            Map<String, List<String>> headerMap = new HashMap<>();
            Map mapExtra = CONTEXT_ADD_HEADERS.get();

            if (mapExtra != null) {
                headerMap.putAll(mapExtra);
            }

            for (String name : headers.keys()) {
                Metadata.Key key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                Iterable<Object> iterable = headers.getAll(key);
                List<String> values = new ArrayList<>();

                if (iterable != null) {
                    for (Object o : iterable) {
                        values.add(String.valueOf(o));
                    }
                }

                headerMap.put(name, values);
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

            EndpointConfig ec = EndpointConfig.builder().build();

            Span span = OpenTracingContextKey.getKey().get();
            SpanContext spanContext = span == null ? null : span.context();
            SecurityContext context = security.contextBuilder(String.valueOf(SECURITY_COUNTER.incrementAndGet()))
                    .tracingSpan(spanContext)
                    .env(env)
                    .endpointConfig(ec)
                    .build();

            Contexts.context().ifPresent(ctx -> ctx.register(context));

            grpcContext = Context.current().withValue(SECURITY_CONTEXT, context);
        } else {
            grpcContext = Context.current();
        }

        return grpcContext;
    }

    /**
     * Obtain the {@link Security} instance being used.
     *
     * @return  the {@link Security} instance being used
     */
    Security getSecurity() {
        return security;
    }

    /**
     * Obtain the default {@link GrpcSecurityHandler}.
     *
     * @return  the default {@link GrpcSecurityHandler}
     */
    GrpcSecurityHandler getDefaultHandler() {
        return defaultHandler;
    }


    /**
     * Implementation of {@link io.grpc.ForwardingServerCallListener} that attaches a context before
     * dispatching calls to the delegate and detaches them after the call completes.
     */
    private static class ContextualizedServerCallListener<ReqT> extends
        ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
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
