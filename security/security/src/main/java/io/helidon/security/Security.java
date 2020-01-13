/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.spi.AuditProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;
import io.helidon.security.spi.SubjectMappingProvider;

import io.opentracing.Tracer;

/**
 * This class is used to "bootstrap" security and integrate it with other frameworks; runtime
 * main entry point is {@link SecurityContext}.
 * <p>
 * It is possible to configure it manually using {@link #builder()} or use {@link #create(Config)} to initialize using
 * configuration support.
 * <p>
 * Security is constructed from various providers {@link SecurityProvider} and
 * a selection policy {@link ProviderSelectionPolicy} to choose the right one(s) to
 * secure a request.
 *
 * @see #builder()
 * @see #create(Config)
 */
// class cannot be final, so CDI can create a proxy for it
public class Security {
    /**
     * Integration should add a special header to each request. The value will contain the original
     * URI as was issued - for HTTP this is the relative URI including query parameters.
     */
    public static final String HEADER_ORIG_URI = "X_ORIG_URI_HEADER";

    private static final Set<String> RESERVED_PROVIDER_KEYS = Set.of(
            "name",
            "class",
            "is-authentication-provider",
            "is-authorization-provider",
            "is-client-security-provider",
            "is-audit-provider");

    private static final Set<String> CONFIG_INTERNAL_PREFIXES = Set.of(
            "provider-policy",
            "providers",
            "environment"
    );

    private static final Logger LOGGER = Logger.getLogger(Security.class.getName());

    private final Collection<Class<? extends Annotation>> annotations = new LinkedList<>();
    private final List<Consumer<AuditProvider.TracedAuditEvent>> auditors = new LinkedList<>();
    private final Optional<SubjectMappingProvider> subjectMappingProvider;
    private final String instanceUuid;
    private final ProviderSelectionPolicy providerSelectionPolicy;
    private final Tracer securityTracer;
    private final SecurityTime serverTime;
    private final Supplier<ExecutorService> executorService;
    private final Config securityConfig;

    @SuppressWarnings("unchecked")
    private Security(Builder builder) {
        this.instanceUuid = UUID.randomUUID().toString();
        this.serverTime = builder.serverTime;
        this.executorService = builder.executorService;
        this.annotations.addAll(SecurityUtil.getAnnotations(builder.allProviders));
        this.securityTracer = SecurityUtil.getTracer(builder.tracingEnabled, builder.tracer);
        this.subjectMappingProvider = Optional.ofNullable(builder.subjectMappingProvider);
        this.securityConfig = builder.config;

        //providers
        List<NamedProvider<AuthorizationProvider>> atzProviders = new LinkedList<>();
        List<NamedProvider<AuthenticationProvider>> atnProviders = new LinkedList<>();
        List<NamedProvider<OutboundSecurityProvider>> outboundProviders = new LinkedList<>();

        atzProviders.addAll(builder.atzProviders);
        atnProviders.addAll(builder.atnProviders);
        outboundProviders.addAll(builder.outboundProviders);

        builder.auditProviders.forEach(auditProvider -> auditors.add(auditProvider.auditConsumer()));

        audit(instanceUuid, SecurityAuditEvent.info(
                AuditEvent.SECURITY_TYPE_PREFIX + ".configure",
                "Security initialized. Providers: audit: \"%s\"; authn: \"%s\"; authz: \"%s\"; identity propagation: \"%s\";")
                .addParam(AuditEvent.AuditParam.plain("auditProviders", SecurityUtil.forAudit(builder.auditProviders)))
                .addParam(AuditEvent.AuditParam.plain("authenticationProvider", SecurityUtil.forAuditNamed(atnProviders)))
                .addParam(AuditEvent.AuditParam.plain("authorizationProvider", SecurityUtil.forAuditNamed(atzProviders)))
                .addParam(AuditEvent.AuditParam
                                  .plain("identityPropagationProvider", SecurityUtil.forAuditNamed(outboundProviders)))
        );

        // the "default" providers
        NamedProvider<AuthenticationProvider> authnProvider = builder.authnProvider;
        NamedProvider<AuthorizationProvider> authzProvider = builder.authzProvider;

        // now I have all providers configured, I can resolve provider selection policy
        providerSelectionPolicy = builder.providerSelectionPolicy.apply(new ProviderSelectionPolicy.Providers() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends SecurityProvider> List<NamedProvider<T>> getProviders(Class<T> providerType) {
                if (providerType.equals(AuthenticationProvider.class)) {
                    List<NamedProvider<T>> result = new LinkedList<>();

                    result.add((NamedProvider<T>) authnProvider);
                    atnProviders.stream()
                            // remove the default provider, it was added as first
                            .filter(pr -> pr != authnProvider)
                            .forEach(atn -> result.add((NamedProvider<T>) atn));
                    return result;
                } else if (providerType.equals(AuthorizationProvider.class)) {
                    List<NamedProvider<T>> result = new LinkedList<>();

                    result.add((NamedProvider<T>) authzProvider);
                    atzProviders.stream()
                            // remove the default provider, it was added as first
                            .filter(pr -> pr != authzProvider)
                            .forEach(atn -> result.add((NamedProvider<T>) atn));
                    return result;
                } else if (providerType.equals(OutboundSecurityProvider.class)) {
                    List<NamedProvider<T>> result = new LinkedList<>();
                    outboundProviders.forEach(atn -> result.add((NamedProvider<T>) atn));
                    return result;
                } else {
                    throw new SecurityException(
                            "Security only supports AuthenticationProvider, AuthorizationProvider and OutboundSecurityProvider in"
                                    + " provider selection policy, not " + providerType.getName());
                }
            }
        });
    }

    /**
     * Creates new instance based on configuration values.
     * <p>
     *
     * @param config Config instance located on security configuration ("providers" is an expected child)
     * @return new instance.
     */
    public static Security create(Config config) {
        Objects.requireNonNull(config, "Configuration must not be null");
        return builder()
                .config(config)
                .build();
    }

    /**
     * Creates new instance based on configuration values.
     * <p>
     *
     * @param config Config instance located on security configuration ("providers" is an expected child)
     * @return new instance.
     */
    public static Builder builder(Config config) {
        Objects.requireNonNull(config, "Configuration must not be null");
        return builder()
                .config(config);
    }

    /**
     * Creates {@link Builder} class.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get a set of roles the subject has, based on {@link Role Role}.
     * This is the set of roles as assumed by authentication provider. Authorization providers may use a different set of
     * roles (and context used authorization provider to check {@link SecurityContext#isUserInRole(String)}).
     *
     * @param subject Subject of a user/service
     * @return set of roles the user/service is in
     */
    public static Set<String> getRoles(Subject subject) {
        return subject.grants(Role.class)
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    void audit(String tracingId, AuditEvent event) {
        // must build within scope of the audit method, as we want to send our caller...

        AuditProvider.AuditSource auditSource = AuditProvider.AuditSource.create();
        for (Consumer<AuditProvider.TracedAuditEvent> auditor : auditors) {
            auditor.accept(SecurityUtil.wrapEvent(tracingId, auditSource, event));
        }
    }

    /**
     * Time that is decisive for the server. This usually returns
     * accessor to current time in a specified time zone.
     * <p>
     * {@link SecurityTime} may be configured to a fixed point in time, intended for
     * testing purposes.
     *
     * @return time to access current time for security decisions
     */
    public SecurityTime serverTime() {
        return serverTime;
    }

    Supplier<ExecutorService> executorService() {
        return executorService;
    }

    ProviderSelectionPolicy providerSelectionPolicy() {
        return providerSelectionPolicy;
    }

    /**
     * Create a new security context builder to build and instance.
     * This is expected to be invoked for each request/response exchange
     * that may be authenticated, authorized etc. Context holds the security subject...
     * Once your processing is done and you no longer want to keep security context, call {@link SecurityContext#logout()} to
     * clear subject and principals.
     *
     * @param id to use when logging, auditing etc. (e.g. some kind of tracing id). If none or empty, security instance
     *           UUID will be used (at least to map all audit records for a single instance of security component). If
     *           defined, security will prefix this id with security instance UUID
     * @return new fluent API builder to create a {@link SecurityContext}
     */
    public SecurityContext.Builder contextBuilder(String id) {
        String newId = ((null == id) || id.isEmpty()) ? (instanceUuid + ":?") : (instanceUuid + ":" + id);
        return new SecurityContext.Builder(this)
                .id(newId)
                .executorService(executorService)
                .tracingTracer(securityTracer)
                .serverTime(serverTime);
    }

    /**
     * Create a new security context with the defined id and all defaults.
     *
     * @param id id of this context
     * @return new security context
     */
    public SecurityContext createContext(String id) {
        return contextBuilder(id).build();
    }

    /**
     * Returns a tracer that can be used to construct new spans.
     *
     * @return {@link Tracer}, may be a no-op tracer if tracing is disabled
     */
    public Tracer tracer() {
        return securityTracer;
    }

    /**
     * Get the complete set of annotations expected by (all) security providers configured.
     * This is to be used for integration with other frameworks that support annotations.
     *
     * @return Collection of annotations expected by configured providers.
     */
    public Collection<Class<? extends Annotation>> customAnnotations() {
        return annotations;
    }

    /**
     * The configuration of security.
     * <p>
     * This method will NOT return security internal configuration:
     * <ul>
     * <li>provider-policy</li>
     * <li>providers</li>
     * <li>environment</li>
     * </ul>
     *
     * @param child the name of the child node to retrieve from config
     * @return a child node of security configuration
     * @throws IllegalArgumentException in case you request child in one of the forbidden trees
     */
    public Config configFor(String child) {
        String test = child.trim();
        if (test.isEmpty()) {
            throw new IllegalArgumentException("Root of security configuration is not available");
        }
        for (String prefix : CONFIG_INTERNAL_PREFIXES) {
            if (child.equals(prefix) || child.startsWith(prefix + ".")) {
                throw new IllegalArgumentException("Security configuration for " + prefix + " is not available");
            }
        }

        return securityConfig.get(child);
    }

    Optional<? extends AuthenticationProvider> resolveAtnProvider(String providerName) {
        return resolveProvider(AuthenticationProvider.class, providerName);
    }

    @SuppressWarnings("unchecked")
    Optional<AuthorizationProvider> resolveAtzProvider(String providerName) {
        return resolveProvider(AuthorizationProvider.class, providerName);
    }

    List<? extends OutboundSecurityProvider> resolveOutboundProvider(String providerName) {
        if (null != providerName) {
            return resolveProvider(OutboundSecurityProvider.class, providerName).map(List::of)
                    .orElse(List.of());
        }
        return providerSelectionPolicy.selectOutboundProviders();
    }

    @SuppressWarnings("unchecked")
    private <T extends SecurityProvider> Optional<T> resolveProvider(Class<T> providerClass, String providerName) {
        if (null == providerName) {
            return providerSelectionPolicy.selectProvider(providerClass);
        }

        Optional<T> instance = providerSelectionPolicy.selectProvider(providerClass, providerName);

        if (instance.isPresent()) {
            return instance;
        }

        throw new SecurityException("Named " + providerClass
                .getSimpleName() + " expected for name \"" + providerName + "\" yet none is configured for such a name");
    }

    /**
     * Security environment builder, to be used to create
     * environment for evaluating security in integration components.
     *
     * @return builder to build {@link SecurityEnvironment}
     */
    public SecurityEnvironment.Builder environmentBuilder() {
        return SecurityEnvironment.builder(serverTime);
    }

    /**
     * Subject mapping provider used to map subject(s) authenticated by {@link io.helidon.security.spi.AuthenticationProvider}
     *  to a new {@link io.helidon.security.Subject}, e.g. to add roles.
     *
     * @return subject mapping provider to use or empty if none defined
     */
    public Optional<SubjectMappingProvider> subjectMapper() {
        return subjectMappingProvider;
    }

    /**
     * Builder pattern class for helping create {@link Security} in a convenient way.
     */
    public static final class Builder implements io.helidon.common.Builder<Security> {
        private final Set<AuditProvider> auditProviders = new LinkedHashSet<>();
        private final List<NamedProvider<AuthenticationProvider>> atnProviders = new LinkedList<>();
        private final List<NamedProvider<AuthorizationProvider>> atzProviders = new LinkedList<>();
        private final List<NamedProvider<OutboundSecurityProvider>> outboundProviders = new LinkedList<>();
        private final Map<SecurityProvider, Boolean> allProviders = new IdentityHashMap<>();

        private NamedProvider<AuthenticationProvider> authnProvider;
        private NamedProvider<AuthorizationProvider> authzProvider;
        private SubjectMappingProvider subjectMappingProvider;
        private Config config = Config.empty();
        private Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy> providerSelectionPolicy =
                FirstProviderSelectionPolicy::new;
        private Tracer tracer;
        private boolean tracingEnabled = true;
        private SecurityTime serverTime = SecurityTime.builder().build();
        private Supplier<ExecutorService> executorService = ThreadPoolSupplier.create();

        private Builder() {
        }

        /**
         * Set the provider selection policy.
         * The function is used to provider an immutable instance of the {@link ProviderSelectionPolicy}.
         * <p>
         * Default is {@link FirstProviderSelectionPolicy}.
         * <p>
         * Alternative built-in policy is: {@link CompositeProviderSelectionPolicy} - you can use its {@link
         * CompositeProviderSelectionPolicy#builder()}
         * to configure it and then configure this method with {@link CompositeProviderSelectionPolicy.Builder#build()}.
         * <p>
         * You can also use custom policy.
         *
         * @param pspFunction function to obtain an instance of the policy. This function will be only called once by security.
         * @return updated builder instance
         */
        public Builder providerSelectionPolicy(Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy>
                                                       pspFunction) {
            this.providerSelectionPolicy = pspFunction;
            return this;
        }

        /**
         * Server time to use when evaluating security policies that depend on time.
         *
         * @param time time instance with possible time shift, explicit timezone or overridden values
         * @return updated builder instance
         */
        public Builder serverTime(SecurityTime time) {
            this.serverTime = time;
            return this;
        }

        /**
         * Set an open tracing tracer to use for security.
         *
         * @param tracer Tracer to use. If null is set, tracing will be disabled.
         * @return updated builder instance
         */
        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            tracingEnabled(null != tracer);
            return this;
        }

        /**
         * Whether or not tracing should be enabled. If set to false, security tracer will be a no-op tracer.
         *
         * @param tracingEnabled true to enable tracing, false to disable
         * @return updated builder instance
         */
        public Builder tracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
            return this;
        }

        /**
         * Disable open tracing support in this security instance. This will cause method {@link SecurityContext#tracer()} to
         * return a no-op tracer.
         *
         * @return updated builder instance
         */
        public Builder disableTracing() {
            return tracingEnabled(false);
        }

        /**
         * Add a provider, works as {@link #addProvider(SecurityProvider, String)}, where the name is set to {@link
         * Class#getSimpleName()}.
         *
         * @param provider Provider implementing multiple security provider interfaces
         * @return updated builder instance
         */
        public Builder addProvider(SecurityProvider provider) {
            return addProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Add a provider, works as {@link #addProvider(SecurityProvider, String)}, where the name is set to {@link
         * Class#getSimpleName()}.
         *
         * @param providerBuilder Builder of a provider, method build will be immediately called
         * @return updated builder instance
         */
        public Builder addProvider(Supplier<? extends SecurityProvider> providerBuilder) {
            return addProvider(providerBuilder.get());
        }

        /**
         * Adds a named provider that may implement multiple interfaces. This is a helper method to allow you to invoke
         * a builder method just once.
         * This method will work as a chained call of add&lt;Provider&gt; for each provider interface your instance implements.
         *
         * @param provider Provider implementing multiple security provider interfaces
         * @param name     name of the provider, if null, this provider will not be referencable from other scopes
         * @return updated builder instance
         */
        public Builder addProvider(SecurityProvider provider, String name) {
            Objects.requireNonNull(provider);

            if (provider instanceof AuthenticationProvider) {
                addAuthenticationProvider((AuthenticationProvider) provider, name);
            }
            if (provider instanceof AuthorizationProvider) {
                addAuthorizationProvider((AuthorizationProvider) provider, name);
            }
            if (provider instanceof OutboundSecurityProvider) {
                addOutboundSecurityProvider((OutboundSecurityProvider) provider, name);
            }
            if (provider instanceof AuditProvider) {
                addAuditProvider((AuditProvider) provider);
            }
            if (provider instanceof SubjectMappingProvider) {
                subjectMappingProvider((SubjectMappingProvider) provider);
            }

            return this;
        }

        /**
         * Adds a named provider that may implement multiple interfaces. This is a helper method to allow you to invoke
         * a builder method just once.
         * This method will work as a chained call of add&lt;Provider&gt; for each provider interface your instance implements.
         *
         * @param providerBuilder Builder of provider implementing multiple security provider interfaces
         * @param name            name of the provider, if null, this provider will not be referencable from other scopes
         * @return updated builder instance
         */
        public Builder addProvider(Supplier<? extends SecurityProvider> providerBuilder, String name) {
            return addProvider(providerBuilder.get(), name);
        }

        /**
         * Set the default authentication provider.
         *
         * @param provider Provider instance to use as the default for this runtime.
         * @return updated builder instance
         */
        public Builder authenticationProvider(AuthenticationProvider provider) {
            // explicit default provider
            this.authnProvider = new NamedProvider<>(provider.getClass().getSimpleName(), provider);
            return addAuthenticationProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Set the default authentication provider.
         *
         * @param builder Builder of provider to use as the default for this runtime.
         * @return updated builder instance
         */
        public Builder authenticationProvider(Supplier<? extends AuthenticationProvider> builder) {
            return authenticationProvider(builder.get());
        }

        /**
         * Set the default authorization provider.
         *
         * @param provider provider instance to use as the default for this runtime.
         * @return updated builder instance
         */
        public Builder authorizationProvider(AuthorizationProvider provider) {
            // explicit default provider
            this.authzProvider = new NamedProvider<>(provider.getClass().getSimpleName(), provider);
            return addAuthorizationProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Set the default authorization provider.
         *
         * @param builder Builder of provider to use as the default for this runtime.
         * @return updated builder instance
         */
        public Builder authorizationProvider(Supplier<? extends AuthorizationProvider> builder) {
            return authorizationProvider(builder.get());
        }

        /**
         * Add an authentication provider. If default isn't set yet, sets it as default.
         * Works as {@link #addAuthenticationProvider(AuthenticationProvider, String)} where the name
         * is simple class name.
         *
         * @param provider provider instance to add
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(AuthenticationProvider provider) {
            return addAuthenticationProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Add an authentication provider. If default isn't set yet, sets it as default.
         * Works as {@link #addAuthenticationProvider(AuthenticationProvider, String)} where the name
         * is simple class name.
         *
         * @param builder builder of provider to add
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(Supplier<? extends AuthenticationProvider> builder) {
            return addAuthenticationProvider(builder.get());
        }

        /**
         * Add a named authentication provider. Provider can be referenced by name e.g. from configuration.
         *
         * @param provider provider instance
         * @param name     name of provider, may be null or empty, but as such will not be rerefencable by name
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(AuthenticationProvider provider, String name) {
            Objects.requireNonNull(provider);

            NamedProvider<AuthenticationProvider> namedProvider = new NamedProvider<>(name, provider);

            if (null == authnProvider) {
                this.authnProvider = namedProvider;
            }
            this.atnProviders.add(namedProvider);
            this.allProviders.put(provider, true);
            return this;
        }

        /**
         * Add a named authentication provider. Provider can be referenced by name e.g. from configuration.
         *
         * @param builder builder of provider instance
         * @param name    name of provider, may be null or empty, but as such will not be rerefencable by name
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(Supplier<? extends AuthenticationProvider> builder,
                                                 String name) {
            return addAuthenticationProvider(builder.get(), name);
        }

        /**
         * Add authorization provider. If there is no default yet, it will become the default.
         *
         * @param provider provider instance
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(AuthorizationProvider provider) {
            return addAuthorizationProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Add authorization provider. If there is no default yet, it will become the default.
         *
         * @param builder builder of provider instance
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(Supplier<? extends AuthorizationProvider> builder) {
            return addAuthorizationProvider(builder.get());
        }

        /**
         * Add a named authorization provider. Named authorization provider can be referenced, such as from
         * configuration.
         *
         * @param provider provider instance
         * @param name     name of provider, may be null or empty, but as such will not be referencable
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(AuthorizationProvider provider, String name) {
            Objects.requireNonNull(provider);

            NamedProvider<AuthorizationProvider> namedProvider = new NamedProvider<>(name, provider);

            if (null == authzProvider) {
                this.authzProvider = namedProvider;
            }
            this.atzProviders.add(namedProvider);
            this.allProviders.put(provider, true);
            return this;
        }

        /**
         * Add a named authorization provider. Named authorization provider can be referenced, such as from
         * configuration.
         *
         * @param builder builder of provider instance
         * @param name    name of provider, may be null or empty, but as such will not be referencable
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(Supplier<? extends AuthorizationProvider> builder, String name) {
            return addAuthorizationProvider(builder.get(), name);
        }

        /**
         * All configured identity propagation providers are used.
         * The first provider to return true to
         * {@link OutboundSecurityProvider#isOutboundSupported(ProviderRequest, SecurityEnvironment, EndpointConfig)}
         * will be called to process current request. Others will be ignored.
         *
         * @param provider Provider instance
         * @return updated builder instance
         */
        public Builder addOutboundSecurityProvider(OutboundSecurityProvider provider) {
            return addOutboundSecurityProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * All configured identity propagation providers are used.
         * The first provider to return true to
         * {@link OutboundSecurityProvider#isOutboundSupported(ProviderRequest, SecurityEnvironment, EndpointConfig)}
         * will be called to process current request. Others will be ignored.
         *
         * @param builder Builder of provider instance
         * @return updated builder instance
         */
        public Builder addOutboundSecurityProvider(Supplier<? extends OutboundSecurityProvider> builder) {
            return addOutboundSecurityProvider(builder.get());
        }

        /**
         * Add a named outbound security provider. Explicit names can be used when using
         * secured client - see integration with Jersey.
         *
         * @param build Builder of provider to use
         * @param name  name of the provider for reference from configuration
         * @return updated builder instance.
         */
        public Builder addOutboundSecurityProvider(Supplier<? extends OutboundSecurityProvider> build,
                                                   String name) {
            return addOutboundSecurityProvider(build.get(), name);
        }

        /**
         * Add a named outbound security provider.
         *
         * @param provider Provider to use
         * @param name     name of the provider for reference from configuration
         * @return updated builder instance.
         */
        public Builder addOutboundSecurityProvider(OutboundSecurityProvider provider, String name) {
            Objects.requireNonNull(provider);
            Objects.requireNonNull(name);

            this.outboundProviders.add(new NamedProvider<>(name, provider));
            this.allProviders.put(provider, true);

            return this;
        }

        /**
         * Add an audit provider to this security runtime.
         * All configured audit providers are used.
         *
         * @param provider provider instance
         * @return updated builder instance
         */
        public Builder addAuditProvider(AuditProvider provider) {
            this.auditProviders.add(provider);
            this.allProviders.put(provider, true);
            return this;
        }

        /**
         * Configure a subject mapping provider that would be used once authentication is processed.
         * Allows you to add {@link Grant Grants} to {@link Subject} or modify it in other ways.
         *
         * @param provider provider to use for subject mapping
         * @return updated builder instance
         */
        public Builder subjectMappingProvider(SubjectMappingProvider provider) {
            this.subjectMappingProvider = provider;
            this.allProviders.put(provider, true);
            return this;
        }

        /**
         * Add an audit provider to this security runtime.
         * All configured audit providers are used.
         *
         * @param builder Builder of provider instance
         * @return updated builder instance
         */
        public Builder addAuditProvider(Supplier<? extends AuditProvider> builder) {
            return addAuditProvider(builder.get());
        }

        /**
         * Add config instance to this builder. This may be later use by components initialized as a side-effect
         * of creating an instance of security (such as security providers).
         *
         * @param config Config instance
         * @return this instance
         */
        public Builder config(Config config) {
            this.config = config;
            fromConfig(config);
            return this;
        }

        /**
         * Builds configured Security instance.
         *
         * @return built instance.
         */
        @Override
        public Security build() {
            if (allProviders.isEmpty()) {
                LOGGER.warning("Security component is NOT configured with any security providers.");
            }

            if (auditProviders.isEmpty()) {
                DefaultAuditProvider provider = DefaultAuditProvider.create(config);
                addAuditProvider(provider);
            }

            if (atnProviders.isEmpty()) {
                addAuthenticationProvider(context -> CompletableFuture
                        .completedFuture(AuthenticationResponse.success(SecurityContext.ANONYMOUS)), "default");
            }

            if (atzProviders.isEmpty()) {
                addAuthorizationProvider(context -> CompletableFuture
                        .completedFuture(AuthorizationResponse.permit()), "default");
            }

            return new Security(this);
        }

        private void fromConfig(Config config) {
            config.get("environment.server-time").as(SecurityTime::create).ifPresent(this::serverTime);
            executorSupplier(ThreadPoolSupplier.create(config.get("environment.executor-service")));

            Map<String, SecurityProviderService> configKeyToService = new HashMap<>();
            Map<String, SecurityProviderService> classNameToService = new HashMap<>();

            //add all providers from service loaders
            String knownKeys = loadProviderServices(configKeyToService, classNameToService);

            config.get("tracing.enabled").as(Boolean.class).ifPresent(this::tracingEnabled);
            //iterate through all providers and find them
            config.get("providers")
                    .asList(Config.class)
                    .ifPresent(confList -> {
                        confList.forEach(pConf -> providerFromConfig(configKeyToService, classNameToService, knownKeys, pConf));
                    });


            String defaultAtnProvider = config.get("default-authentication-provider").asString().orElse(null);
            if (null != defaultAtnProvider) {
                authenticationProvider(atnProviders.stream()
                                               .filter(nsp -> nsp.getName().equals(defaultAtnProvider))
                                               .findFirst()
                                               .map(NamedProvider::getProvider)
                                               .orElseThrow(() -> new SecurityException("Authentication provider named \""
                                                                                                + defaultAtnProvider + "\" is set"
                                                                                                + " as "
                                                                                                + "default, yet no provider "
                                                                                                + "configuration exists")));
            }

            String defaultAtzProvider = config.get("default-authorization-provider").asString().orElse(null);
            if (null != defaultAtzProvider) {
                authorizationProvider(atzProviders.stream()
                                              .filter(nsp -> nsp.getName().equals(defaultAtzProvider))
                                              .findFirst()
                                              .map(NamedProvider::getProvider)
                                              .orElseThrow(() -> new SecurityException("Authorization provider named \""
                                                                                               + defaultAtzProvider + "\" is set"
                                                                                               + " as "
                                                                                               + "default, yet no provider "
                                                                                               + "configuration exists")));
            }

            // now policy
            config = config.get("provider-policy");
            ProviderSelectionPolicyType pType = config.get("type")
                    .asString()
                    .map(ProviderSelectionPolicyType::valueOf)
                    .orElse(ProviderSelectionPolicyType.FIRST);

            switch (pType) {
            case FIRST:
                providerSelectionPolicy = FirstProviderSelectionPolicy::new;
                break;
            case COMPOSITE:
                providerSelectionPolicy = CompositeProviderSelectionPolicy.create(config);
                break;
            case CLASS:
                providerSelectionPolicy = findProviderSelectionPolicy(config);
                break;
            default:
                throw new IllegalStateException("Invalid enum option: " + pType + ", probably version mis-match");
            }
        }

        private void providerFromConfig(Map<String, SecurityProviderService> configKeyToService,
                                        Map<String, SecurityProviderService> classNameToService, String knownKeys, Config pConf) {
            AtomicReference<SecurityProviderService> service = new AtomicReference<>();
            AtomicReference<Config> providerSpecific = new AtomicReference<>();

            // if we have name and class, use them
            String className = pConf.get("class").asString().orElse(null);

            if (null == className) {
                findProviderService(configKeyToService, knownKeys, pConf, service, providerSpecific);
            } else {
                // explicit class name - the most detailed configuration possible
                SecurityProviderService providerService = classNameToService.get(className);
                if (null == providerService) {
                    findProviderSpecificConfig(pConf, providerSpecific);
                } else {
                    service.set(providerService);
                    providerSpecific.set(pConf.get(providerService.providerConfigKey()));
                }
            }

            Config providerSpecificConfig = providerSpecific.get();
            SecurityProviderService providerService = service.get();

            if ((null == className) && (null == providerService)) {
                throw new SecurityException(
                        "Each configured provider MUST have a \"class\" configuration property defined or a custom "
                                + "configuration section mapped to that provider, supported keys: " + knownKeys);
            }

            String name = resolveProviderName(pConf, className, providerSpecificConfig, providerService);
            boolean isAuthn = pConf.get("is-authentication-provider").asBoolean().orElse(true);
            boolean isAuthz = pConf.get("is-authorization-provider").asBoolean().orElse(true);
            boolean isClientSec = pConf.get("is-client-security-provider").asBoolean().orElse(true);
            boolean isAudit = pConf.get("is-audit-provider").asBoolean().orElse(true);
            boolean isSubjectMapper = pConf.get("is-subject-mapper").asBoolean().orElse(true);

            SecurityProvider provider;
            if (null == providerService) {
                provider = SecurityUtil.instantiate(className, SecurityProvider.class, providerSpecificConfig);
            } else {
                provider = providerService.providerInstance(providerSpecificConfig);
            }

            if (isAuthn && (provider instanceof AuthenticationProvider)) {
                addAuthenticationProvider((AuthenticationProvider) provider, name);
            }
            if (isAuthz && (provider instanceof AuthorizationProvider)) {
                addAuthorizationProvider((AuthorizationProvider) provider, name);
            }
            if (isClientSec && (provider instanceof OutboundSecurityProvider)) {
                addOutboundSecurityProvider((OutboundSecurityProvider) provider, name);
            }
            if (isAudit && (provider instanceof AuditProvider)) {
                addAuditProvider((AuditProvider) provider);
            }
            if (isSubjectMapper && (provider instanceof SubjectMappingProvider)) {
                subjectMappingProvider((SubjectMappingProvider) provider);
            }
        }

        private void executorSupplier(Supplier<ExecutorService> supplier) {
            this.executorService = supplier;
        }

        private String resolveProviderName(Config pConf,
                                           String className,
                                           Config providerSpecificConfig,
                                           SecurityProviderService providerService) {
            return pConf.get("name").asString().orElseGet(() -> {
                if (null == providerSpecificConfig) {
                    if (null == className) {
                        return providerService.providerClass().getSimpleName();
                    } else {
                        int index = className.indexOf('.');
                        if (index > -1) {
                            return className.substring(index + 1);
                        }
                        return className;
                    }
                } else {
                    return providerSpecificConfig.name();
                }
            });
        }

        private void findProviderSpecificConfig(Config pConf, AtomicReference<Config> providerSpecific) {
            // no service for this class, must choose the configuration by selection
            pConf.asNodeList().get().stream().filter(this::notReservedProviderKey).forEach(providerSpecificConf -> {
                if (!providerSpecific.compareAndSet(null, providerSpecificConf)) {
                    throw new SecurityException("More than one provider configurations found, each provider can only"
                                                        + " have one provide specific config. Conflict: "
                                                        + providerSpecific.get().key()
                                                        + " and " + providerSpecificConf.key());
                }
            });
        }

        private void findProviderService(Map<String, SecurityProviderService> configKeyToService,
                                         String knownKeys,
                                         Config pConf,
                                         AtomicReference<SecurityProviderService> service,
                                         AtomicReference<Config> providerSpecific) {
            // everything else is based on provider specific configuration
            pConf.asNodeList().get().stream().filter(this::notReservedProviderKey).forEach(providerSpecificConf -> {
                if (!providerSpecific.compareAndSet(null, providerSpecificConf)) {
                    throw new SecurityException("More than one provider configurations found, each provider can only"
                                                        + " have one provider specific config. Conflict: "
                                                        + providerSpecific.get().key()
                                                        + " and " + providerSpecificConf.key());
                }

                String keyName = providerSpecificConf.name();
                if (configKeyToService.containsKey(keyName)) {
                    service.set(configKeyToService.get(keyName));
                } else {
                    throw new SecurityException("Configuration key " + providerSpecificConf.key()
                                                        + " is not a valid provider configuration. Supported keys: "
                                                        + knownKeys);
                }
            });
        }

        private String loadProviderServices(Map<String, SecurityProviderService> configKeyToService,
                                            Map<String, SecurityProviderService> classNameToService) {

            Set<String> configKeys = new HashSet<>();
            ServiceLoader<SecurityProviderService> loader = ServiceLoader.load(SecurityProviderService.class);
            loader.forEach(service -> {
                String configKey = service.providerConfigKey();
                if (null != configKey) {
                    configKeyToService.put(configKey, service);
                    configKeys.add(configKey);
                }
                Class<? extends SecurityProvider> theClass = service.providerClass();
                classNameToService.put(theClass.getName(), service);
            });
            return String.join(", ", configKeys);
        }

        private boolean notReservedProviderKey(Config config) {
            return !RESERVED_PROVIDER_KEYS.contains(config.name());
        }

        private Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy> findProviderSelectionPolicy(Config config) {
            Class clazz = config.get("class-name").as(Class.class).orElseThrow(() -> new java.lang.SecurityException(
                    "You have configured a CLASS provider selection without configuring class-name"));

            if (!ProviderSelectionPolicy.class.isAssignableFrom(clazz)) {
                throw new SecurityException("Class " + clazz.getName() + " does not implement ProviderSelectionPolicy");
            }

            @SuppressWarnings("unchecked") Class<? extends ProviderSelectionPolicy> pspClass = clazz;

            //now let's find the constructor
            try {
                Constructor<? extends ProviderSelectionPolicy> constructor = pspClass
                        .getConstructor(ProviderSelectionPolicy.Providers.class, Config.class);
                // java9
                //if (constructor.canAccess(null)) {
                // java8
                if (ReflectionUtil.canAccess(getClass(), constructor)) {
                    return providers -> {
                        try {
                            return constructor.newInstance(providers, config);
                        } catch (Exception e) {
                            throw new SecurityException("Failed to instantiate ProviderSelectionPolicy", e);
                        }
                    };
                } else {
                    throw new SecurityException("Constructor " + constructor + " of class " + clazz
                            .getName() + " is not accessible");
                }
            } catch (NoSuchMethodException ignored) {
                // ignore
            }

            try {
                Constructor<? extends ProviderSelectionPolicy> constructor = pspClass
                        .getConstructor(ProviderSelectionPolicy.Providers.class);
                // java9
                //if (constructor.canAccess(null)) {
                // java8
                if (ReflectionUtil.canAccess(getClass(), constructor)) {
                    return providers -> {
                        try {
                            return constructor.newInstance(providers);
                        } catch (Exception e) {
                            throw new SecurityException("Failed to instantiate ProviderSelectionPolicy", e);
                        }
                    };
                } else {
                    throw new SecurityException("Constructor " + constructor + " of class " + clazz
                            .getName() + " is not accessible");
                }
            } catch (NoSuchMethodException e) {
                throw new SecurityException("You have configured " + clazz
                        .getName() + " as provider selection policy class, yet it is missing public constructor with Providers "
                                                    + "or Providers and Config as parameters.",
                                            e);
            }
        }

        /**
         * Check whether any provider is configured.
         * @return {@code true} if no provider is configured, {@code false} if there is at least one provider configured
         */
        public boolean noProvider() {
            return allProviders.isEmpty();
        }
    }
}
