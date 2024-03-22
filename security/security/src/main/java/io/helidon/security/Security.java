/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigValue;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.security.spi.AuditProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.DigestProvider;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderConfig;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecretsProvider;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;
import io.helidon.security.spi.SubjectMappingProvider;
import io.helidon.tracing.Tracer;

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
public interface Security {
    /**
     * Integration should add a special header to each request. The value will contain the original
     * URI as was issued - for HTTP this is the relative URI including query parameters.
     */
    String HEADER_ORIG_URI = "X_ORIG_URI_HEADER";

    /**
     * Creates new instance based on configuration values.
     *
     * @param config Config instance located on security configuration ("providers" is an expected child)
     * @return new instance.
     */
    static Security create(io.helidon.common.config.Config config) {
        Objects.requireNonNull(config, "Configuration must not be null");
        return builder()
                .config(config)
                .build();
    }

    /**
     * Creates new instance based on configuration values.
     *
     * @param config Config instance located on security configuration ("providers" is an expected child)
     * @return new instance.
     */
    static Builder builder(Config config) {
        Objects.requireNonNull(config, "Configuration must not be null");
        return builder()
                .config(config);
    }

    /**
     * Creates {@link io.helidon.security.Security.Builder} class.
     *
     * @return builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Get a set of roles the subject has, based on {@link io.helidon.security.Role Role}.
     * This is the set of roles as assumed by authentication provider. Authorization providers may use a different set of
     * roles (and context used authorization provider to check {@link io.helidon.security.SecurityContext#isUserInRole(String)}).
     *
     * @param subject Subject of a user/service
     * @return set of roles the user/service is in
     */
    static Set<String> getRoles(Subject subject) {
        return subject.grants(Role.class)
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Time that is decisive for the server. This usually returns
     * accessor to current time in a specified time zone.
     * <p>
     * {@link io.helidon.security.SecurityTime} may be configured to a fixed point in time, intended for
     * testing purposes.
     *
     * @return time to access current time for security decisions
     */
    SecurityTime serverTime();

    /**
     * Create a new security context builder to build and instance.
     * This is expected to be invoked for each request/response exchange
     * that may be authenticated, authorized etc. Context holds the security subject...
     * Once your processing is done and you no longer want to keep security context, call
     * {@link io.helidon.security.SecurityContext#logout()} to
     * clear subject and principals.
     *
     * @param id to use when logging, auditing etc. (e.g. some kind of tracing id). If none or empty, security instance
     *           UUID will be used (at least to map all audit records for a single instance of security component). If
     *           defined, security will prefix this id with security instance UUID
     * @return new fluent API builder to create a {@link io.helidon.security.SecurityContext}
     */
    SecurityContext.Builder contextBuilder(String id);

    /**
     * Create a new security context with the defined id and all defaults.
     *
     * @param id id of this context
     * @return new security context
     */
    SecurityContext createContext(String id);

    /**
     * Returns a tracer that can be used to construct new spans.
     *
     * @return {@link io.helidon.tracing.Tracer}, may be a no-op tracer if tracing is disabled
     */
    Tracer tracer();

    /**
     * Get the complete set of annotations expected by (all) security providers configured.
     * This is to be used for integration with other frameworks that support annotations.
     *
     * @return Collection of annotations expected by configured providers.
     */
    Collection<Class<? extends Annotation>> customAnnotations();

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
    Config configFor(String child);

    /**
     * Encrypt bytes.
     * This method handles the bytes in memory, and as such is not suitable
     * for processing of large amounts of data.
     *
     * @param configurationName name of the configuration of this encryption
     * @param bytesToEncrypt    bytes to encrypt
     * @return future with cipher text
     */
    String encrypt(String configurationName, byte[] bytesToEncrypt);

    /**
     * Decrypt cipher text.
     * This method handles the bytes in memory, and as such is not suitable
     * for processing of large amounts of data.
     *
     * @param configurationName name of the configuration of this encryption
     * @param cipherText        cipher text to decrypt
     * @return future with decrypted bytes
     */
    byte[] decrypt(String configurationName, String cipherText);

    /**
     * Create a digest for the provided bytes.
     *
     * @param configurationName name of the digest configuration
     * @param bytesToDigest     data to digest
     * @param preHashed         whether the data is already a hash
     * @return future with digest (such as signature or HMAC)
     */
    String digest(String configurationName, byte[] bytesToDigest, boolean preHashed);

    /**
     * Create a digest for the provided raw bytes.
     *
     * @param configurationName name of the digest configuration
     * @param bytesToDigest     data to digest
     * @return future with digest (such as signature or HMAC)
     */
    String digest(String configurationName, byte[] bytesToDigest);

    /**
     * Verify a digest.
     *
     * @param configurationName name of the digest configuration
     * @param bytesToDigest     data to verify a digest for
     * @param digest            digest as provided by a third party (or another component)
     * @param preHashed         whether the data is already a hash
     * @return future with result of verification ({@code true} means the digest is valid)
     */
    boolean verifyDigest(String configurationName, byte[] bytesToDigest, String digest, boolean preHashed);

    /**
     * Verify a digest.
     *
     * @param configurationName name of the digest configuration
     * @param bytesToDigest     raw data to verify a digest for
     * @param digest            digest as provided by a third party (or another component)
     * @return future with result of verification ({@code true} means the digest is valid)
     */
    boolean verifyDigest(String configurationName, byte[] bytesToDigest, String digest);

    /**
     * Get a secret.
     *
     * @param configurationName name of the secret configuration
     * @return future with the secret value, or error if the secret is not configured
     */
    Optional<String> secret(String configurationName);

    /**
     * Get a secret.
     *
     * @param configurationName name of the secret configuration
     * @param defaultValue      default value to use if secret not configured
     * @return future with the secret value
     */
    String secret(String configurationName, String defaultValue);

    /**
     * Security environment builder, to be used to create
     * environment for evaluating security in integration components.
     *
     * @return builder to build {@link io.helidon.security.SecurityEnvironment}
     */
    SecurityEnvironment.Builder environmentBuilder();

    /**
     * Subject mapping provider used to map subject(s) authenticated by {@link io.helidon.security.spi.AuthenticationProvider}
     * to a new {@link Subject}, e.g. to add roles.
     *
     * @return subject mapping provider to use or empty if none defined
     */
    Optional<SubjectMappingProvider> subjectMapper();

    /**
     * Whether security is enabled or disabled.
     * Disabled security does not check authorization and authenticates all users as
     * {@link SecurityContext#ANONYMOUS}.
     *
     * @return {@code true} if security is enabled
     */
    boolean enabled();

    /**
     * Audit an event.
     *
     * @param tracingId id to map this audit event to a request
     * @param event event to audit
     */
    void audit(String tracingId, AuditEvent event);

    /**
     * Configured provider selection policy.
     *
     * @return provider selection policy
     */
    ProviderSelectionPolicy providerSelectionPolicy();

    /**
     * Find an authentication provider by name, or use the default if the name is not available.
     *
     * @param providerName name of the provider
     * @return authentication provider if the named one is configured, or a default one is configured, otherwise empty
     */
    Optional<? extends AuthenticationProvider> resolveAtnProvider(String providerName);

    /**
     * Find an authorization provider by name, or use the default if the name is not available.
     *
     * @param providerName name of the provider
     * @return authorization provider if the named one is configured, or a default one is configured, otherwise empty
     */
    Optional<AuthorizationProvider> resolveAtzProvider(String providerName);

    /**
     * Find outbound provider(s) by name, or use the default if the name is not available.
     *
     * @param providerName name of the provider
     * @return outbound providers to use
     */
    List<? extends OutboundSecurityProvider> resolveOutboundProvider(String providerName);

    /**
     * Builder pattern class for helping create {@link io.helidon.security.Security} in a convenient way.
     */
    @Configured(root = true, prefix = "security", description = "Configuration of security providers, integration and other"
            + " security options")
     final class Builder implements io.helidon.common.Builder<Builder, Security> {
        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        private final Set<AuditProvider> auditProviders = new LinkedHashSet<>();
        private final List<NamedProvider<AuthenticationProvider>> atnProviders = new LinkedList<>();
        private final List<NamedProvider<AuthorizationProvider>> atzProviders = new LinkedList<>();
        private final List<NamedProvider<OutboundSecurityProvider>> outboundProviders = new LinkedList<>();
        private final Map<String, SecretsProvider<?>> secretsProviders = new HashMap<>();
        private final Map<String, EncryptionProvider<?>> encryptionProviders = new HashMap<>();
        private final Map<String, DigestProvider<?>> digestProviders = new HashMap<>();
        private final Map<SecurityProvider, Boolean> allProviders = new IdentityHashMap<>();

        private final Map<String, Supplier<Optional<String>>> secrets = new HashMap<>();
        private final Map<String, EncryptionProvider.EncryptionSupport> encryptions = new HashMap<>();
        private final Map<String, DigestProvider.DigestSupport> digests = new HashMap<>();
        private final Set<String> providerNames = new HashSet<>();
        private NamedProvider<AuthenticationProvider> authnProvider;
        private NamedProvider<AuthorizationProvider> authzProvider;
        private SubjectMappingProvider subjectMappingProvider;
        private Config config = Config.empty();
        private Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy> providerSelectionPolicy =
                FirstProviderSelectionPolicy::new;
        private Tracer tracer;
        private boolean tracingEnabled = true;
        private SecurityTime serverTime = SecurityTime.builder().build();
        private boolean enabled = true;

        private Builder() {
        }

        /**
         * Set the provider selection policy.
         * The function is used to provider an immutable instance of the {@link io.helidon.security.spi.ProviderSelectionPolicy}.
         * <p>
         * Default is {@link io.helidon.security.FirstProviderSelectionPolicy}.
         * <p>
         * Alternative built-in policy is: {@link io.helidon.security.CompositeProviderSelectionPolicy} - you can use its {@link
         * io.helidon.security.CompositeProviderSelectionPolicy#builder()}
         * to configure it and then configure this method with
         * {@link io.helidon.security.CompositeProviderSelectionPolicy.Builder#build()}.
         * <p>
         * You can also use custom policy.
         *
         * @param pspFunction function to obtain an instance of the policy. This function will be only called once by security.
         * @return updated builder instance
         */
        @ConfiguredOption(key = "provider-policy.type", type = ProviderSelectionPolicyType.class,
                          description = "Type of the policy.", value = "FIRST")
        @ConfiguredOption(key = "provider-policy.class-name", description = "Provider selection policy class name, only used "
                + "when type is set to CLASS", type = Class.class)
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
        @ConfiguredOption(key = "environment.server-time")
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
        @ConfiguredOption(key = "tracing.enabled", value = "true")
        public Builder tracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
            return this;
        }

        /**
         * Disable open tracing support in this security instance. This will cause method
         * {@link io.helidon.security.SecurityContext#tracer()} to
         * return a no-op tracer.
         *
         * @return updated builder instance
         */
        public Builder disableTracing() {
            return tracingEnabled(false);
        }

        /**
         * Add a provider, works as {@link #addProvider(io.helidon.security.spi.SecurityProvider, String)}, where the name is set
         * to {@link
         * Class#getSimpleName()}.
         *
         * @param provider Provider implementing multiple security provider interfaces
         * @return updated builder instance
         */
        @ConfiguredOption(key = "providers", kind = ConfiguredOption.Kind.LIST, required = true, provider = true)
        public Builder addProvider(SecurityProvider provider) {
            return addProvider(provider, provider.getClass().getSimpleName());
        }

        /**
         * Add a provider, works as {@link #addProvider(io.helidon.security.spi.SecurityProvider, String)}, where the name is set
         * to {@link
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
        @ConfiguredOption(key = "default-authentication-provider",
                          description = "ID of the default authentication provider",
                          type = String.class,
                          provider = true)
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
        @ConfiguredOption(key = "default-authorization-provider",
                          type = String.class,
                          description = "ID of the default authorization provider")
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
         * Works as {@link #addAuthenticationProvider(io.helidon.security.spi.AuthenticationProvider, String)} where the name
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
         * Works as {@link #addAuthenticationProvider(io.helidon.security.spi.AuthenticationProvider, String)} where the name
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
            if (null != name) {
                this.providerNames.add(name);
            }
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
            if (null != name) {
                this.providerNames.add(name);
            }
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
         * {@link io.helidon.security.spi.OutboundSecurityProvider#isOutboundSupported(io.helidon.security.ProviderRequest,
         * io.helidon.security.SecurityEnvironment, io.helidon.security.EndpointConfig)}
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
         * {@link io.helidon.security.spi.OutboundSecurityProvider#isOutboundSupported(io.helidon.security.ProviderRequest,
         * io.helidon.security.SecurityEnvironment, io.helidon.security.EndpointConfig)}
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
            this.providerNames.add(name);

            return this;
        }

        /**
         * Add a named secret provider.
         *
         * @param provider provider to use
         * @param name     name of the provider for reference from configuration
         * @return updated builder instance
         */
        public Builder addSecretProvider(SecretsProvider<?> provider, String name) {
            Objects.requireNonNull(provider);
            Objects.requireNonNull(name);

            this.secretsProviders.put(name, provider);
            this.allProviders.put(provider, true);
            this.providerNames.add(name);

            return this;
        }

        /**
         * Add a named encryption provider.
         *
         * @param provider provider to use
         * @param name     name of the provider for reference from configuration
         * @return updated builder instance
         */
        public Builder addEncryptionProvider(EncryptionProvider<?> provider, String name) {
            Objects.requireNonNull(provider);
            Objects.requireNonNull(name);

            this.encryptionProviders.put(name, provider);
            this.allProviders.put(provider, true);
            this.providerNames.add(name);

            return this;
        }

        /**
         * Add a named digest provider (providing signatures and possibly HMAC).
         *
         * @param provider provider to use
         * @param name     name of the provider for reference from configuration
         * @return updated builder instance
         */
        public Builder addDigestProvider(DigestProvider<?> provider, String name) {
            Objects.requireNonNull(provider);
            Objects.requireNonNull(name);

            this.digestProviders.put(name, provider);
            this.allProviders.put(provider, true);
            this.providerNames.add(name);

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
         * Allows you to add {@link io.helidon.security.Grant Grants} to {@link io.helidon.security.Subject} or modify it in other
         * ways.
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
        public Builder config(io.helidon.common.config.Config config) {
            this.config = config;
            fromConfig(config);
            return this;
        }

        /**
         * Security can be disabled using configuration, or explicitly.
         * By default, security instance is enabled.
         * Disabled security instance will not perform any checks and allow
         * all requests.
         *
         * @param enabled set to {@code false} to disable security
         * @return updated builder instance
         */
        @ConfiguredOption("true")
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Builds configured Security instance.
         *
         * @return built instance.
         */
        @Override
        public Security build() {
            if (allProviders.isEmpty() && enabled) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Security component is NOT configured with any security providers.");
            }

            if (auditProviders.isEmpty()) {
                DefaultAuditProvider provider = DefaultAuditProvider.create(config);
                addAuditProvider(provider);
            }

            if (atnProviders.isEmpty()) {
                addAuthenticationProvider(context -> AuthenticationResponse.success(SecurityContext.ANONYMOUS), "default");
            }

            if (atzProviders.isEmpty()) {
                addAuthorizationProvider(new DefaultAtzProvider(), "default");
            }

            if (!enabled) {
                providerSelectionPolicy(FirstProviderSelectionPolicy::new);
            }

            return new SecurityImpl(this);
        }

        /**
         * Add a secret to security configuration.
         *
         * @param name           name of the secret configuration
         * @param secretProvider security provider handling this secret
         * @param providerConfig security provider configuration for this secret
         * @param <T>            type of the provider specific configuration object
         * @return updated builder instance
         * @see #secret(String)
         * @see #secret(String, String)
         */
        @ConfiguredOption(key = "secrets",
                          kind = ConfiguredOption.Kind.LIST,
                          type = Config.class,
                          description = "Configured secrets")
        @ConfiguredOption(key = "secrets.*.name", type = String.class, description = "Name of the secret, used for lookup")
        @ConfiguredOption(key = "secrets.*.provider", type = String.class, description = "Name of the secret provider")
        @ConfiguredOption(key = "secrets.*.config",
                          type = SecretsProviderConfig.class,
                          provider = true,
                          description = "Configuration specific to the secret provider")
        public <T extends ProviderConfig> Builder addSecret(String name,
                                                            SecretsProvider<T> secretProvider,
                                                            T providerConfig) {

            secrets.put(name, secretProvider.secret(providerConfig));
            return this;
        }

        /**
         * Add an encryption to security configuration.
         *
         * @param name               name of the encryption configuration
         * @param encryptionProvider security provider handling this encryption
         * @param providerConfig     security provider configuration for this encryption
         * @param <T>                type of the provider specific configuration object
         * @return updated builder instance
         * @see #encrypt(String, byte[])
         * @see #decrypt(String, String)
         */
        public <T extends ProviderConfig> Builder addEncryption(String name,
                                                                EncryptionProvider<T> encryptionProvider,
                                                                T providerConfig) {

            encryptions.put(name, encryptionProvider.encryption(providerConfig));
            return this;
        }

        /**
         * Add a signature/HMAC to security configuration.
         *
         * @param name           name of the digest configuration
         * @param digestProvider security provider handling this digest
         * @param providerConfig security provider configuration for this digest
         * @param <T>            type of the provider specific configuration object
         * @return updated builder instance
         * @see #digest(String, byte[])
         * @see #digest(String, byte[], boolean)
         * @see #verifyDigest(String, byte[], String)
         * @see #verifyDigest(String, byte[], String, boolean)
         */
        public <T extends ProviderConfig> Builder addDigest(String name,
                                                            DigestProvider<T> digestProvider,
                                                            T providerConfig) {

            digests.put(name, digestProvider.digest(providerConfig));
            return this;
        }

        private void fromConfig(io.helidon.common.config.Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);

            if (!enabled) {
                LOGGER.log(System.Logger.Level.INFO, "Security is disabled, ignoring provider configuration");
                return;
            }

            config.get("environment.server-time").map(SecurityTime::create).ifPresent(this::serverTime);

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
            io.helidon.common.config.Config providerPolicyConfig = config.get("provider-policy");
            ProviderSelectionPolicyType pType = providerPolicyConfig.get("type")
                    .asString()
                    .map(ProviderSelectionPolicyType::valueOf)
                    .orElse(ProviderSelectionPolicyType.FIRST);

            switch (pType) {
            case FIRST:
                providerSelectionPolicy = FirstProviderSelectionPolicy::new;
                break;
            case COMPOSITE:
                providerSelectionPolicy = CompositeProviderSelectionPolicy.create(providerPolicyConfig);
                break;
            case CLASS:
                providerSelectionPolicy = findProviderSelectionPolicy(providerPolicyConfig);
                break;
            default:
                throw new IllegalStateException("Invalid enum option: " + pType + ", probably version mis-match");
            }

            config.get("secrets")
                    .asList(Config.class)
                    .ifPresent(confList -> {
                        confList.forEach(sConf -> {
                            String name = sConf.get("name").asString().get();
                            String provider = sConf.get("provider").asString().get();
                            Config secretConfig = sConf.get("config");
                            SecretsProvider<?> secretsProvider = secretsProviders.get(provider);
                            if (secretsProvider == null) {
                                throw new SecurityException("Provider \"" + provider
                                                                    + "\" used for secret \"" + name + "\" not found");
                            } else {
                                secrets.put(name, secretsProvider.secret(secretConfig));
                            }
                        });
                    });

            config.get("encryption")
                    .asList(Config.class)
                    .ifPresent(confList -> {
                        confList.forEach(eConf -> {
                            String name = eConf.get("name").asString().get();
                            String provider = eConf.get("provider").asString().get();
                            Config encryptionConfig = eConf.get("config");
                            EncryptionProvider<?> encryptionProvider = encryptionProviders.get(provider);
                            if (encryptionProvider == null) {
                                throw new SecurityException("Provider \"" + provider
                                                                    + "\" used for encryption \"" + name + "\" not found");
                            } else {
                                encryptions.put(name, encryptionProvider.encryption(encryptionConfig));
                            }
                        });
                    });

            config.get("digest")
                    .asList(Config.class)
                    .ifPresent(confList -> {
                        confList.forEach(dConf -> {
                            String name = dConf.get("name").asString().get();
                            String provider = dConf.get("provider").asString().get();
                            Config digestConfig = dConf.get("config");
                            DigestProvider<?> digestProvider = digestProviders.get(provider);
                            if (digestProvider == null) {
                                throw new SecurityException("Provider \"" + provider
                                                                    + "\" used for digest \"" + name + "\" not found");
                            } else {
                                digests.put(name, digestProvider.digest(digestConfig));
                            }
                        });
                    });
        }

        private void providerFromConfig(Map<String, SecurityProviderService> configKeyToService,
                                        Map<String, SecurityProviderService> classNameToService,
                                        String knownKeys,
                                        Config pConf) {
            boolean enabled = pConf.get("enabled").asBoolean().orElse(true);
            if (!enabled) {
                // this provider is marked as disabled, we will ignore it
                // this is checking the top level provider configuration (see below check for provider specific)
                // this section check (example):
                /*
                security.providers:
                    - type: "some-type
                      enabled: false
                 */
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Provider with key: " + pConf.key() + " is disabled");
                }
                return;
            }

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

            if (providerSpecificConfig != null
                    &&!providerSpecificConfig.get("enabled").asBoolean().orElse(true)) {
                // this provider is marked as disabled, we will ignore it
                // this is within the provider specific configuration, to support both simple lists (checked above)
                // and nested provider configuration; this section check (example):
                /*
                security.providers:
                    - oidc:
                        enabled: false
                 */

                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Provider: " + name + " is disabled");
                }
                return;
            }

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
            if (provider instanceof SecretsProvider) {
                addSecretProvider((SecretsProvider<?>) provider, name);
            }
            if (provider instanceof EncryptionProvider) {
                addEncryptionProvider((EncryptionProvider<?>) provider, name);
            }
            if (provider instanceof DigestProvider) {
                addDigestProvider((DigestProvider<?>) provider, name);
            }
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

            ConfigValue<String> type = pConf.get("type").asString();
            if (type.isPresent()) {
                // explicit type, ignore search below
                findProviderService(service, configKeyToService, type.get(), knownKeys);
                providerSpecific.set(pConf.get(type.get()));
            } else {
                // everything else is based on provider specific configuration
                pConf.asNodeList().get().stream().filter(this::notReservedProviderKey).forEach(providerSpecificConf -> {
                    if (!providerSpecific.compareAndSet(null, providerSpecificConf)) {
                        throw new SecurityException("More than one provider configurations found, each provider can only"
                                                            + " have one provider specific config. Conflict: "
                                                            + providerSpecific.get().key()
                                                            + " and " + providerSpecificConf.key());
                    }

                    findProviderService(service, configKeyToService, providerSpecificConf.name(), knownKeys);
                });
            }
        }

        private void findProviderService(AtomicReference<SecurityProviderService> service,
                                         Map<String, SecurityProviderService> configKeyToService,
                                         String name,
                                         String knownKeys) {
            if (configKeyToService.containsKey(name)) {
                service.set(configKeyToService.get(name));
            } else {
                throw new SecurityException("Configuration key " + name
                                                    + " is not a valid provider configuration. Supported keys: "
                                                    + knownKeys);
            }
        }

        private String loadProviderServices(Map<String, SecurityProviderService> configKeyToService,
                                            Map<String, SecurityProviderService> classNameToService) {

            Set<String> configKeys = new HashSet<>();
            HelidonServiceLoader<SecurityProviderService> loader =
                    HelidonServiceLoader.create(ServiceLoader.load(SecurityProviderService.class));

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
            return !SecurityImpl.RESERVED_PROVIDER_KEYS.contains(config.name());
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
         *
         * @param providerClass type of provider of interest (can be {@link io.helidon.security.spi.AuthenticationProvider} and
         *                      other interfaces implementing {@link io.helidon.security.spi.SecurityProvider})
         * @return {@code true} if no provider is configured, {@code false} if there is at least one provider configured
         */
        public boolean noProvider(Class<? extends SecurityProvider> providerClass) {
            if (providerClass.equals(AuthenticationProvider.class)) {
                return atnProviders.isEmpty();
            }
            if (providerClass.equals(AuthorizationProvider.class)) {
                return atzProviders.isEmpty();
            }
            if (providerClass.equals(OutboundSecurityProvider.class)) {
                return outboundProviders.isEmpty();
            }
            if (providerClass.equals(AuditProvider.class)) {
                return auditProviders.isEmpty();
            }
            if (providerClass.equals(SubjectMappingProvider.class)) {
                return subjectMappingProvider == null;
            }

            return allProviders.isEmpty();
        }

        /**
         * Check whether a provider with the name is configured.
         *
         * @param name name of a provider
         * @return true if such a provider is configured
         */
        public boolean hasProvider(String name) {
            return providerNames
                    .stream()
                    .anyMatch(name::equals);
        }

        Set<AuditProvider> auditProviders() {
            return auditProviders;
        }

        List<NamedProvider<AuthenticationProvider>> atnProviders() {
            return atnProviders;
        }

        List<NamedProvider<AuthorizationProvider>> atzProviders() {
            return atzProviders;
        }

        List<NamedProvider<OutboundSecurityProvider>> outboundProviders() {
            return outboundProviders;
        }

        Map<String, SecretsProvider<?>> secretsProviders() {
            return secretsProviders;
        }

        Map<String, EncryptionProvider<?>> encryptionProviders() {
            return encryptionProviders;
        }

        Map<String, DigestProvider<?>> digestProviders() {
            return digestProviders;
        }

        Map<SecurityProvider, Boolean> allProviders() {
            return allProviders;
        }

        Map<String, Supplier<Optional<String>>> secrets() {
            return secrets;
        }

        Map<String, EncryptionProvider.EncryptionSupport> encryptions() {
            return encryptions;
        }

        Map<String, DigestProvider.DigestSupport> digests() {
            return digests;
        }

        Set<String> providerNames() {
            return providerNames;
        }

        NamedProvider<AuthenticationProvider> authnProvider() {
            return authnProvider;
        }

        NamedProvider<AuthorizationProvider> authzProvider() {
            return authzProvider;
        }

        SubjectMappingProvider subjectMappingProvider() {
            return subjectMappingProvider;
        }

        Config config() {
            return config;
        }

        Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy> providerSelectionPolicy() {
            return providerSelectionPolicy;
        }

        Tracer tracer() {
            return tracer;
        }

        boolean tracingEnabled() {
            return tracingEnabled;
        }

        SecurityTime serverTime() {
            return serverTime;
        }

        boolean enabled() {
            return enabled;
        }

        private static class DefaultAtzProvider implements AuthorizationProvider {
            @Override
            public AuthorizationResponse authorize(ProviderRequest context) {
                return AuthorizationResponse.permit();
            }
        }
    }
}
