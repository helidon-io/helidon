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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Method;
import io.helidon.http.PathMatchers;
import io.helidon.http.UnauthorizedException;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
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
 * </ul>
 * <p>
 * Example:
 * <pre>
 * // WebServer routing builder - this is our integration point
 * {@link io.helidon.webserver.http.HttpRouting} routing = HttpRouting.builder()
 * // register the WebSecurity to create context (shared by all routes)
 * .register({@link SecurityHttpFeature}.{@link
 * SecurityHttpFeature#create(Security) from(security)})
 * </pre>
 * <p>
 * Other methods are to create security enforcement points (gates) for routes (e.g. you are expected to use them for a get, post
 * etc. routes on specific path).
 * These methods are starting points that provide an instance of {@link SecurityHandler} that has finer grained methods to
 * control the gate behavior. <br>
 * Note that if any gate is configured, auditing will be enabled by default except for GET and HEAD methods - if you want
 * to audit any method, invoke {@link SecurityFeature#audit()} to create a gate that will always audit the route.
 * If you want to create a gate and not audit it, use {@link SecurityHandler#skipAudit()} on the returned instance.
 * <ul>
 * <li>{@link SecurityFeature#secure()} - authentication and authorization</li>
 * <li>{@link SecurityFeature#rolesAllowed(String...)} - role based access control (implies authentication and authorization)</li>
 * <li>{@link SecurityFeature#authenticate()} - authentication only</li>
 * <li>{@link SecurityFeature#authorize()} - authorization only</li>
 * <li>{@link SecurityFeature#allowAnonymous()} - authentication optional</li>
 * <li>{@link SecurityFeature#audit()} - audit all requests (including GET and HEAD)</li>
 * <li>{@link SecurityFeature#authenticator(String)} - use explicit authenticator (named - as configured in config or through
 * builder)</li>
 * <li>{@link SecurityFeature#authorizer(String)} - use explicit authorizer (named - as configured in config or through
 * builder)</li>
 * <li>{@link SecurityFeature#enforce()} - use defaults (e.g. no authentication, authorization, audit calls except for GET and
 * HEAD); this
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
 */
public final class SecurityHttpFeature implements HttpSecurity, HttpFeature, Weighted {
    /**
     * Security can accept additional headers to be added to security request.
     * This will be used to obtain multivalue string map (a map of string to list of strings) from context (appropriate
     * to the integration).
     */
    public static final String CONTEXT_ADD_HEADERS = "security.addHeaders";
    /**
     * Security can accept additional headers to be added to security request.
     * This will be used to propagate additional headers from successful security response to the final server response.
     */
    public static final String CONTEXT_RESPONSE_HEADERS = "security.responseHeaders";

    private static final Logger LOGGER = Logger.getLogger(SecurityHttpFeature.class.getName());

    private final Security security;
    private final SecurityHandler defaultHandler;
    private final double weight;
    private final List<PathsConfig> configs;

    private SecurityHttpFeature(Security security, double weight, SecurityHandler defaults, List<PathsConfig> configs) {
        this.security = security;
        this.weight = weight;
        this.defaultHandler = defaults;
        this.configs = configs;
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
    public static SecurityHttpFeature create(Security security) {
        return SecurityFeature.builder()
                .security(security)
                .build()
                .routingFeature();
    }

    /**
     * Create a consumer of routing config to be
     * {@link io.helidon.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)  registered} with
     * web server routing to process security requests.
     * This method configures security and web server integration from a config instance.
     *
     * @param config on the node of the server configuration of security (expects {@code paths} for example),
     *               configuration of security is expected under root node {@code security}
     * @return routing config consumer
     */
    public static SecurityHttpFeature create(Config config) {
        return SecurityFeature.builder()
                .security(Security.create(config.root().get("security")))
                .config(config)
                .build()
                .routingFeature();
    }

    static SecurityHttpFeature create(Security security,
                                      double weight,
                                      SecurityHandler defaults,
                                      List<PathsConfig> configs) {
        return new SecurityHttpFeature(security, weight, defaults, configs);
    }

    /**
     * Create a new web security instance using the default handler as base defaults for all handlers used.
     * If handlers are loaded from config, than this is the least significant value.
     *
     * @param defaultHandler if a security handler is configured for a route, it will take its defaults from this handler
     * @return new instance of web security with the handler default
     */
    public SecurityHttpFeature securityDefaults(SecurityHandler defaultHandler) {
        Objects.requireNonNull(defaultHandler, "Default security handler must not be null");
        return new SecurityHttpFeature(security, weight, defaultHandler, configs);
    }

    @Override
    public void setup(HttpRouting.Builder rules) {
        if (!security.enabled()) {
            LOGGER.info("Security is disabled. Not registering any security handlers");
            return;
        }
        rules.security(this);
        rules.addFilter(new SecurityContextFilter(security, defaultHandler));
        registerRouting(rules);
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

    @Override
    public double weight() {
        return weight;
    }

    private void registerRouting(HttpRules routing) {
        for (PathsConfig config : configs) {
            SecurityHandler pathHandler = SecurityHandler.builder()
                    .from(defaultHandler.prototype())
                    .from(config.handler().prototype())
                    .build();

            if (config.methods().isEmpty()) {
                routing.any(config.path(), pathHandler);
            } else {
                routing.route(Method.predicate(config.methods()),
                              PathMatchers.create(config.path()),
                              pathHandler);
            }
        }
    }
}
