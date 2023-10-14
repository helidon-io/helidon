/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Method;
import io.helidon.http.PathMatchers;
import io.helidon.http.UnauthorizedException;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityException;
import io.helidon.tracing.Span;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Integration of security into WebServer.
 * <p>
 * Methods that start with "from" are to register WebSecurity with {@link io.helidon.webserver.WebServer}
 * - to create {@link SecurityContext} for requests:
 * <ul>
 * <li>{@link #create(Security)}</li>
 * <li>{@link #create(Config)}</li>
 * <li>{@link #create(Security, Config)}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * // WebServer routing builder - this is our integration point
 * {@link io.helidon.webserver.http.HttpRouting} routing = HttpRouting.builder()
 * // register the WebSecurity to create context (shared by all routes)
 * .register({@link SecurityFeature}.{@link
 * SecurityFeature#create(Security) from(security)})
 * </pre>
 * <p>
 * Other methods are to create security enforcement points (gates) for routes (e.g. you are expected to use them for a get, post
 * etc. routes on specific path).
 * These methods are starting points that provide an instance of {@link SecurityHandler} that has finer grained methods to
 * control the gate behavior. <br>
 * Note that if any gate is configured, auditing will be enabled by default except for GET and HEAD methods - if you want
 * to audit any method, invoke {@link #audit()} to create a gate that will always audit the route.
 * If you want to create a gate and not audit it, use {@link SecurityHandler#skipAudit()} on the returned instance.
 * <ul>
 * <li>{@link #secure()} - authentication and authorization</li>
 * <li>{@link #rolesAllowed(String...)} - role based access control (implies authentication and authorization)</li>
 * <li>{@link #authenticate()} - authentication only</li>
 * <li>{@link #authorize()} - authorization only</li>
 * <li>{@link #allowAnonymous()} - authentication optional</li>
 * <li>{@link #audit()} - audit all requests (including GET and HEAD)</li>
 * <li>{@link #authenticator(String)} - use explicit authenticator (named - as configured in config or through builder)</li>
 * <li>{@link #authorizer(String)} - use explicit authorizer (named - as configured in config or through builder)</li>
 * <li>{@link #enforce()} - use defaults (e.g. no authentication, authorization, audit calls except for GET and HEAD); this
 * also give access to more fine-grained methods of {@link SecurityHandler}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * // continue from example above...
 * // create a gate for method GET: authenticate all paths under /user and require role "user" for authorization
 * .get("/user[/{*}]", WebSecurity.{@link SecurityFeature#rolesAllowed(String...)
 * rolesAllowed("user")})
 * </pre>
 * @deprecated Please use {@link io.helidon.webserver.security.SecurityServerFeature} instead, as it can cover multiple
 *  sockets
 */
@Deprecated(forRemoval = true)
public final class SecurityFeature implements  HttpSecurity, HttpFeature, Weighted {
    /**
     * Security can accept additional headers to be added to security request.
     * This will be used to obtain multivalue string map (a map of string to list of strings) from context (appropriate
     * to the integration).
     */
    public static final String CONTEXT_ADD_HEADERS = "security.addHeaders";

    private static final Logger LOGGER = Logger.getLogger(SecurityFeature.class.getName());
    private static final AtomicInteger SECURITY_COUNTER = new AtomicInteger();
    private static final double WEIGHT = 800;

    private final Security security;
    private final Config config;
    private final SecurityHandler defaultHandler;
    private final double weight;

    private SecurityFeature(Security security, Config config) {
        this(security, config, SecurityHandler.create());
    }

    private SecurityFeature(Security security, Config config, SecurityHandler defaultHandler) {
        this.security = security;
        this.config = config;
        this.defaultHandler = defaultHandler;
        this.weight = (config == null) ? WEIGHT : config.get("web-server.weight").asDouble().orElse(WEIGHT);
    }

    /**
     * Create a consumer of routing config to be
     * {@link io.helidon.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)   registered} with
     * web server routing to process security requests.
     * This method is to be used together with other routing methods to protect web resources programmatically.
     * Example:
     * <pre>
     * .get("/user[/{*}]", WebSecurity.authenticate()
     * .rolesAllowed("user"))
     * </pre>
     *
     * @param security initialized security
     * @return routing config consumer
     */
    public static SecurityFeature create(Security security) {
        return new SecurityFeature(security, null);
    }

    /**
     * Create a consumer of routing config to be
     * {@link io.helidon.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)  registered} with
     * web server routing to process security requests.
     * This method configures security and web server integration from a config instance
     *
     * @param config Config instance to load security and web server integration from configuration
     * @return routing config consumer
     */
    public static SecurityFeature create(Config config) {
        Security security = Security.create(config);
        return create(security, config);
    }

    /**
     * Create a consumer of routing config to be
     * {@link io.helidon.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)  registered} with
     * web server routing to process security requests.
     * This method expects initialized security and creates web server integration from a config instance
     *
     * @param security Security instance to use
     * @param config   Config instance to load security and web server integration from configuration
     * @return routing config consumer
     */
    public static SecurityFeature create(Security security, Config config) {
        return new SecurityFeature(security, config);
    }

    /**
     * Secure access using authentication and authorization.
     * Auditing is enabled by default for methods modifying content.
     * When using RBAC (role based access control), just use {@link #rolesAllowed(String...)}.
     * If you use a security provider, that requires additional data, use {@link SecurityHandler#customObject(Object)}.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled if provider configured</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link SecurityHandler} instance configured with authentication and authorization
     */
    public static SecurityHandler secure() {
        return SecurityHandler.create().authenticate().authorize();
    }

    /**
     * If called, request will go through authentication process - defaults to false (even if authorize is true).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler authenticate() {
        return SecurityHandler.create().authenticate();
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
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler audit() {
        return SecurityHandler.create().audit();
    }

    /**
     * Use a named authenticator (as supported by security - if not defined, default authenticator is used).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link Security}
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler authenticator(String explicitAuthenticator) {
        return SecurityHandler.create().authenticate().authenticator(explicitAuthenticator);
    }

    /**
     * Use a named authorizer (as supported by security - if not defined, default authorizer is used, if none defined, all is
     * permitted).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled with explicit provider</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @param explicitAuthorizer name of authorizer as configured in {@link Security}
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler authorizer(String explicitAuthorizer) {
        return SecurityHandler.create().authenticate().authorize().authorizer(explicitAuthorizer);
    }

    /**
     * An array of allowed roles for this path - must have a security provider supporting roles.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @param roles if subject is any of these roles, allow access
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler rolesAllowed(String... roles) {
        return SecurityHandler.create().rolesAllowed(roles);

    }

    /**
     * If called, authentication failure will not abort request and will continue as anonymous (defaults to false).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and optional</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler allowAnonymous() {
        return SecurityHandler.create().authenticate().authenticationOptional();
    }

    /**
     * Enable authorization for this route.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled if provider is present</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler authorize() {
        return SecurityHandler.create().authorize();
    }

    /**
     * Return a default instance to create a default enforcement point (or modify the result further).
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: not modified (default: disabled)</li>
     * <li>Authorization: not modified (default: disabled)</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link SecurityHandler} instance
     */
    public static SecurityHandler enforce() {
        return SecurityHandler.create();
    }

    /**
     * Create a new web security instance using the default handler as base defaults for all handlers used.
     * If handlers are loaded from config, than this is the least significant value.
     *
     * @param defaultHandler if a security handler is configured for a route, it will take its defaults from this handler
     * @return new instance of web security with the handler default
     */
    public SecurityFeature securityDefaults(SecurityHandler defaultHandler) {
        Objects.requireNonNull(defaultHandler, "Default security handler must not be null");
        return new SecurityFeature(security, config, defaultHandler);
    }

    @Override
    public void setup(HttpRouting.Builder rules) {
        if (!security.enabled()) {
            LOGGER.info("Security is disabled. Not registering any security handlers");
            return;
        }
        rules.security(this);
        rules.addFilter(this::registerContext);

        if (null != config) {
            // only configure routing if we were asked to do so (otherwise it must be configured by hand on web server)
            registerRouting(rules);
        }
    }

    @Override
    public boolean authenticate(ServerRequest request, ServerResponse response, boolean requiredHint)
            throws UnauthorizedException {
        // if the authentication is required and we were not configured to handle this already, just throw
        if (requiredHint) {
            if (!request.context()
                    .get(SecurityContext.class)
                    .map(SecurityContext::isAuthenticated)
                    .orElse(false)) {
                throw new UnauthorizedException("User not authenticated");
            }
        }
        return true;
    }

    @Override
    public boolean authorize(ServerRequest request, ServerResponse response, String... roleHint) throws ForbiddenException {
        Optional<SecurityContext> maybeContext = request.context().get(SecurityContext.class);

        if (maybeContext.isEmpty()) {
            if (roleHint.length == 0) {
                return true;
            }
            throw new ForbiddenException("This endpoint is restricted");
        }

        SecurityContext ctx = maybeContext.get();

        if (roleHint.length == 0) {
            if (!ctx.isAuthorized()) {
                throw new ForbiddenException("This endpoint is restricted");
            }
            return true;
        }
        if (!ctx.isAuthorized()) {
            for (String role : roleHint) {
                if (ctx.isUserInRole(role)) {
                    return true;
                }
            }
            throw new ForbiddenException("This endpoint is restricted");
        }
        // authorized through security already
        return true;
    }

    private void registerContext(FilterChain chain, ServerRequest req, ServerResponse res) {
        // todo use Headers instead, this is not case insensitive
        Map<String, List<String>> allHeaders = new HashMap<>(req.headers().toMap());

        Context context = req.context();
        Optional<Map> newHeaders = context.get(CONTEXT_ADD_HEADERS, Map.class);
        newHeaders.ifPresent(allHeaders::putAll);

        //make sure there is no context
        if (context.get(SecurityContext.class).isEmpty()) {
            SecurityEnvironment env = security.environmentBuilder()
                    .targetUri(req.requestedUri().toUri())
                    .path(req.path().path())
                    .method(req.prologue().method().text())
                    .addAttribute("remotePeer", req.remotePeer())
                    .addAttribute("userIp", req.remotePeer().host())
                    .addAttribute("userPort", req.remotePeer().port())
                    .transport(req.isSecure() ? "https" : "http")
                    .headers(allHeaders)
                    .build();
            EndpointConfig ec = EndpointConfig.builder()
                    .build();

            SecurityContext.Builder contextBuilder = security.contextBuilder(String.valueOf(SECURITY_COUNTER.incrementAndGet()))
                    .env(env)
                    .endpointConfig(ec);

            // only register if exists
            Span.current().ifPresent(it -> contextBuilder.tracingSpan(it.context()));

            SecurityContext securityContext = contextBuilder.build();

            context.register(securityContext);
            context.register(defaultHandler);
        }

        chain.proceed();
    }

    @Override
    public double weight() {
        return weight;
    }

    private void registerRouting(HttpRules routing) {
        Config wsConfig = config.get("web-server");
        SecurityHandler defaults = SecurityHandler.create(wsConfig.get("defaults"), defaultHandler);

        ConfigValue<List<Config>> configuredPaths = wsConfig.get("paths").asNodeList();
        if (configuredPaths.isPresent()) {
            List<Config> paths = configuredPaths.get();
            for (Config pathConfig : paths) {
                List<Method> methods = pathConfig.get("methods").asNodeList().orElse(List.of())
                        .stream()
                        .map(Config::asString)
                        .map(ConfigValue::get)
                        .map(Method::create)
                        .collect(Collectors.toList());

                String path = pathConfig.get("path")
                        .asString()
                        .orElseThrow(() -> new SecurityException(pathConfig
                                                                         .key() + " must contain path key with a path to "
                                                                         + "register to web server"));
                if (methods.isEmpty()) {
                    routing.any(path, SecurityHandler.create(pathConfig, defaults));
                } else {
                    routing.route(Method.predicate(methods),
                                  PathMatchers.create(path),
                                  SecurityHandler.create(pathConfig, defaults));
                }
            }
        }
    }
}
