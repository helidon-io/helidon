/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.spi.AuditProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.DigestProvider;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SubjectMappingProvider;
import io.helidon.tracing.Tracer;

final class SecurityImpl implements Security {

    static final Set<String> RESERVED_PROVIDER_KEYS = Set.of(
            "name",
            "type",
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

    private static final Logger LOGGER = Logger.getLogger(SecurityImpl.class.getName());

    private final Collection<Class<? extends Annotation>> annotations = new LinkedList<>();
    private final List<Consumer<AuditProvider.TracedAuditEvent>> auditors = new LinkedList<>();
    private final Optional<SubjectMappingProvider> subjectMappingProvider;
    private final String instanceUuid;
    private final ProviderSelectionPolicy providerSelectionPolicy;
    private final LazyValue<Tracer> securityTracer;
    private final SecurityTime serverTime;
    private final Supplier<ExecutorService> executorService;
    private final Config securityConfig;
    private final boolean enabled;

    private final Map<String, Supplier<Optional<String>>> secrets;
    private final Map<String, EncryptionProvider.EncryptionSupport> encryptions;
    private final Map<String, DigestProvider.DigestSupport> digests;

    @SuppressWarnings("unchecked")
    SecurityImpl(Builder builder) {
        this.enabled = builder.enabled();
        this.instanceUuid = UUID.randomUUID().toString();
        this.serverTime = builder.serverTime();
        this.executorService = builder.executorService();
        this.annotations.addAll(SecurityUtil.getAnnotations(builder.allProviders()));
        this.securityTracer = LazyValue.create(() -> SecurityUtil.getTracer(builder.tracingEnabled(), builder.tracer()));
        this.subjectMappingProvider = Optional.ofNullable(builder.subjectMappingProvider());
        this.securityConfig = builder.config();

        if (!enabled) {
            //security is disabled
            audit(instanceUuid, SecurityAuditEvent.info(
                    AuditEvent.SECURITY_TYPE_PREFIX + ".configure",
                    "Security is disabled."));
        }

        //providers
        List<NamedProvider<AuthorizationProvider>> atzProviders = new LinkedList<>();
        List<NamedProvider<AuthenticationProvider>> atnProviders = new LinkedList<>();
        List<NamedProvider<OutboundSecurityProvider>> outboundProviders = new LinkedList<>();

        atzProviders.addAll(builder.atzProviders());
        atnProviders.addAll(builder.atnProviders());
        outboundProviders.addAll(builder.outboundProviders());

        builder.auditProviders().forEach(auditProvider -> auditors.add(auditProvider.auditConsumer()));

        audit(instanceUuid, SecurityAuditEvent.info(
                        AuditEvent.SECURITY_TYPE_PREFIX + ".configure",
                        "Security initialized. Providers: audit: \"%s\"; authn: \"%s\"; authz: \"%s\"; identity propagation: "
                                + "\"%s\";")
                .addParam(AuditEvent.AuditParam.plain("auditProviders", SecurityUtil.forAudit(builder.auditProviders())))
                .addParam(AuditEvent.AuditParam.plain("authenticationProvider", SecurityUtil.forAuditNamed(atnProviders)))
                .addParam(AuditEvent.AuditParam.plain("authorizationProvider", SecurityUtil.forAuditNamed(atzProviders)))
                .addParam(AuditEvent.AuditParam
                                  .plain("identityPropagationProvider", SecurityUtil.forAuditNamed(outboundProviders)))
        );

        // the "default" providers
        NamedProvider<AuthenticationProvider> authnProvider = builder.authnProvider();
        NamedProvider<AuthorizationProvider> authzProvider = builder.authzProvider();

        // now I have all providers configured, I can resolve provider selection policy
        providerSelectionPolicy = builder.providerSelectionPolicy().apply(new ProviderSelectionPolicy.Providers() {
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

        // secrets and transit security
        this.secrets = Map.copyOf(builder.secrets());
        this.encryptions = Map.copyOf(builder.encryptions());
        this.digests = Map.copyOf(builder.digests());
    }

    @Override
    public SecurityTime serverTime() {
        return serverTime;
    }

    @Override
    public SecurityContext.Builder contextBuilder(String id) {
        String newId = ((null == id) || id.isEmpty()) ? (instanceUuid + ":?") : (instanceUuid + ":" + id);
        return new SecurityContext.Builder(this)
                .id(newId)
                .executorService(executorService)
                .tracingTracer(securityTracer.get())
                .serverTime(serverTime);
    }

    @Override
    public SecurityContext createContext(String id) {
        return contextBuilder(id).build();
    }

    @Override
    public Tracer tracer() {
        return securityTracer.get();
    }

    @Override
    public Collection<Class<? extends Annotation>> customAnnotations() {
        return annotations;
    }

    @Override
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

    @Override
    public String encrypt(String configurationName, byte[] bytesToEncrypt) {
        EncryptionProvider.EncryptionSupport encryption = encryptions.get(configurationName);
        if (encryption == null) {
            throw new SecurityException("There is no configured encryption named " + configurationName);
        }

        return encryption.encrypt(bytesToEncrypt);
    }

    @Override
    public byte[] decrypt(String configurationName, String cipherText) {
        EncryptionProvider.EncryptionSupport encryption = encryptions.get(configurationName);
        if (encryption == null) {
            throw new SecurityException("There is no configured encryption named " + configurationName);
        }

        return encryption.decrypt(cipherText);
    }

    @Override
    public String digest(String configurationName, byte[] bytesToDigest, boolean preHashed) {
        DigestProvider.DigestSupport digest = digests.get(configurationName);
        if (digest == null) {
            throw new SecurityException("There is no configured digest named " + configurationName);
        }
        return digest.digest(bytesToDigest, preHashed);
    }

    @Override
    public String digest(String configurationName, byte[] bytesToDigest) {
        return digest(configurationName, bytesToDigest, false);
    }

    @Override
    public boolean verifyDigest(String configurationName, byte[] bytesToDigest, String digest, boolean preHashed) {
        DigestProvider.DigestSupport digestSupport = digests.get(configurationName);
        if (digest == null) {
            throw new SecurityException("There is no configured digest named " + configurationName);
        }
        return digestSupport.verify(bytesToDigest, preHashed, digest);
    }

    @Override
    public boolean verifyDigest(String configurationName, byte[] bytesToDigest, String digest) {
        return verifyDigest(configurationName, bytesToDigest, digest, false);
    }

    @Override
    public Optional<String> secret(String configurationName) {
        Supplier<Optional<String>> supplier = secrets.get(configurationName);
        if (supplier == null) {
            throw new SecurityException("Secret \"" + configurationName + "\" is not configured.");
        }

        return supplier.get();
    }

    @Override
    public String secret(String configurationName, String defaultValue) {
        Supplier<Optional<String>> supplier = secrets.get(configurationName);
        if (supplier == null) {
            LOGGER.finest(() -> "There is no configured secret named " + configurationName + ", using default value");
            return defaultValue;
        }

        return supplier.get().orElse(defaultValue);
    }

    @Override
    public SecurityEnvironment.Builder environmentBuilder() {
        return SecurityEnvironment.builder(serverTime);
    }

    @Override
    public Optional<SubjectMappingProvider> subjectMapper() {
        return subjectMappingProvider;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void audit(String tracingId, AuditEvent event) {
        // must build within scope of the audit method, as we want to send our caller...

        AuditProvider.AuditSource auditSource = AuditProvider.AuditSource.create();
        for (Consumer<AuditProvider.TracedAuditEvent> auditor : auditors) {
            auditor.accept(SecurityUtil.wrapEvent(tracingId, auditSource, event));
        }
    }

    @Override
    public ProviderSelectionPolicy providerSelectionPolicy() {
        return providerSelectionPolicy;
    }

    @Override
    public Supplier<ExecutorService> executorService() {
        return executorService;
    }

    @Override
    public Optional<? extends AuthenticationProvider> resolveAtnProvider(String providerName) {
        return resolveProvider(AuthenticationProvider.class, providerName);
    }

    @Override
    public Optional<AuthorizationProvider> resolveAtzProvider(String providerName) {
        return resolveProvider(AuthorizationProvider.class, providerName);
    }

    @Override
    public List<? extends OutboundSecurityProvider> resolveOutboundProvider(String providerName) {
        if (null != providerName) {
            return resolveProvider(OutboundSecurityProvider.class, providerName).map(List::of)
                    .orElse(List.of());
        }
        return providerSelectionPolicy.selectOutboundProviders();
    }

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

}
