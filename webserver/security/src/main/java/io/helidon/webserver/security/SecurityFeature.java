/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.security.Security;
import io.helidon.webserver.spi.ServerFeature;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;

/**
 * Server feature for security, to be registered with
 * {@link io.helidon.webserver.WebServerConfig.Builder#addFeature}.
 * <p>
 * This feature adds a filter to register {@link io.helidon.security.SecurityContext}
 * in request {@link io.helidon.common.context.Context},
 * and registers {@link io.helidon.webserver.http.HttpRouting.Builder#security(io.helidon.webserver.http.HttpSecurity)}.
 * If configured, it also adds protection points to endpoints.
 */
@RuntimeType.PrototypedBy(SecurityFeatureConfig.class)
public class SecurityFeature implements Weighted, ServerFeature, RuntimeType.Api<SecurityFeatureConfig> {
    static final double WEIGHT = 800;
    static final String SECURITY_ID = "security";
    private static final System.Logger LOGGER = System.getLogger(SecurityFeature.class.getName());

    private final Security security;
    private final SecurityFeatureConfig featureConfig;

    private SecurityFeature(SecurityFeatureConfig featureConfig) {
        this.security = featureConfig.security();
        this.featureConfig = featureConfig;
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static SecurityFeatureConfig.Builder builder() {
        return SecurityFeatureConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static SecurityFeature create(SecurityFeatureConfig config) {
        return new SecurityFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static SecurityFeature create(Consumer<SecurityFeatureConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Secure access using authentication and authorization.
     * Auditing is enabled by default for methods modifying content.
     * When using RBAC (role based access control), just use {@link #rolesAllowed(String...)}.
     * If you use a security provider, that requires additional data, use {@link io.helidon.webserver.security.SecurityHandler#customObject(Object)}.
     * <p>
     * Behavior:
     * <ul>
     * <li>Authentication: enabled and required</li>
     * <li>Authorization: enabled if provider configured</li>
     * <li>Audit: not modified (default: enabled except for GET and HEAD methods)</li>
     * </ul>
     *
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance configured with authentication and authorization
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * This type replaces for most use cases the {@link SecurityHttpFeature} (intentionally
     * has the same class name, so the use cases are re-visited).
     * <p>
     * This type is discovered automatically by {@link io.helidon.webserver.WebServer}. To configure it, use the
     * {@code server.features.security} configuration node (for mapping of protected paths). Configuration of security itself
     * is still under root node {@code security}.
     *
     * @param explicitAuthenticator name of authenticator as configured in {@link io.helidon.security.Security}
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
     * @see SecurityHttpFeature
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
     * @param explicitAuthorizer name of authorizer as configured in {@link io.helidon.security.Security}
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
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
     * @return {@link io.helidon.webserver.security.SecurityHandler} instance
     */
    public static SecurityHandler enforce() {
        return SecurityHandler.create();
    }

    @Override
    public SecurityFeatureConfig prototype() {
        return featureConfig;
    }

    @Override
    public String name() {
        return featureConfig.name();
    }

    @Override
    public String type() {
        return SECURITY_ID;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!security.enabled()) {
            LOGGER.log(System.Logger.Level.TRACE, "Security is disabled. Not registering any security handlers");
            return;
        }

        SecurityHandler defaults = featureConfig.defaults();
        Set<String> defaultSockets = new HashSet<>();
        SecurityHandlerConfig defaultConfig = defaults.prototype();
        if (defaultConfig.sockets().isEmpty()) {
            defaultSockets.addAll(featureContext.sockets());
            defaultSockets.add(DEFAULT_SOCKET_NAME);
        } else {
            defaultSockets.addAll(defaultConfig.sockets());
        }

        Map<String, List<PathsConfig>> configurations = new HashMap<>();

        List<PathsConfig> paths = featureConfig.paths();
        for (PathsConfig path : paths) {
            List<String> sockets = new ArrayList<>(path.sockets());
            if (sockets.isEmpty()) {
                sockets.addAll(defaultSockets);
            }
            for (String socket : sockets) {
                // add this handler to each configured socket
                configurations.computeIfAbsent(socket, it -> new ArrayList<>())
                        .add(path);
            }
        }
        Set<String> allSockets = new HashSet<>(featureContext.sockets());
        allSockets.add(DEFAULT_SOCKET_NAME);

        configurations.forEach((socketName, configs) -> {
            if (featureContext.socketExists(socketName)) {
                allSockets.remove(socketName);
                SocketBuilders socket = featureContext.socket(socketName);
                SecurityHttpFeature routingFeature = routingFeature(defaults, configs);
                socket.httpRouting().addFeature(routingFeature);
            }
        });

        for (String allSocket : allSockets) {
            // for remaining socket, we still need to register SecurityContext
            SocketBuilders socket = featureContext.socket(allSocket);
            SecurityHttpFeature routingFeature = routingFeature(defaults, List.of());
            socket.httpRouting().addFeature(routingFeature);
        }
    }

    SecurityHttpFeature routingFeature() {
        SecurityHandler defaults = featureConfig.defaults();

        List<PathsConfig> configurations = new ArrayList<>();

        List<PathsConfig> paths = featureConfig.paths();
        for (PathsConfig path : paths) {
            List<String> sockets = new ArrayList<>(path.sockets());
            if (sockets.isEmpty() || sockets.contains(DEFAULT_SOCKET_NAME)) {
                configurations.add(path);
            }
        }

        return SecurityHttpFeature.create(security,
                                          featureConfig.weight(),
                                          defaults,
                                          configurations);
    }

    @Override
    public double weight() {
        return featureConfig.weight();
    }

    private SecurityHttpFeature routingFeature(SecurityHandler defaults, List<PathsConfig> configs) {
        return SecurityHttpFeature.create(security,
                                          featureConfig.weight(),
                                          defaults,
                                          configs);
    }
}
