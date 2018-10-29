/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.internal.SecurityAuditEvent;
import io.helidon.security.spi.AuthorizationProvider;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Security context is task scoped (e.g. each HTTP request will have a single instance).
 */
final class SecurityContextImpl implements SecurityContext {
    private final Security security;
    private final String tracingId;
    private final SpanContext requestSpan;
    private final Supplier<ExecutorService> executorService;
    private final Tracer securityTracer;
    private final SecurityTime serverTime;
    private final ReadWriteLock envLock = new ReentrantReadWriteLock();
    private final ReadWriteLock ecLock = new ReentrantReadWriteLock();

    private volatile SecurityEnvironment environment;
    private volatile EndpointConfig ec;
    private volatile Subject serviceSubject;
    private volatile Subject currentSubject;
    private volatile AtomicBoolean atzChecked = new AtomicBoolean(false);

    SecurityContextImpl(Builder builder) {
        this.security = builder.getSecurity();
        this.tracingId = builder.getId();
        this.requestSpan = builder.getTracingSpan();
        this.executorService = builder.getExecutorServiceSupplier();
        this.securityTracer = builder.getTracingTracer();
        this.serverTime = builder.getServerTime();
        this.environment = builder.getEnv();
        this.ec = builder.getEc();
    }

    @Override
    public SpanContext getTracingSpan() {
        return requestSpan;
    }

    @Override
    public Tracer getTracer() {
        return securityTracer;
    }

    @Override
    public String getId() {
        return tracingId;
    }

    @Override
    public SecurityTime getServerTime() {
        return serverTime;
    }

    @Override
    public SecurityRequestBuilder securityRequestBuilder() {
        return securityRequestBuilder(getEnv());
    }

    @Override
    public SecurityRequestBuilder securityRequestBuilder(SecurityEnvironment environment) {
        return new SecurityRequestBuilder(this);
    }

    @Override
    public SecurityClientBuilder<AuthenticationResponse> atnClientBuilder() {
        return new SecurityClientBuilder<>(
                security,
                this,
                AuthenticationClientImpl::new);
    }

    @Override
    public AuthenticationResponse authenticate() {
        return atnClientBuilder().buildAndGet();
    }

    @Override
    public SecurityClientBuilder<AuthorizationResponse> atzClientBuilder() {
        atzChecked.set(true);
        return new SecurityClientBuilder<>(
                security,
                this,
                AuthorizationClientImpl::new);
    }

    @Override
    public OutboundSecurityClientBuilder outboundClientBuilder() {
        return new OutboundSecurityClientBuilder(security, this);
    }

    @Override
    public boolean isAuthenticated() {
        return getUser().isPresent();

    }

    @Override
    public void logout() {
        currentSubject = ANONYMOUS;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isUserInRole(String role) {
        if (!isAuthenticated()) {
            return false;
        }

        Optional<AuthorizationProvider> authorizationProvider = security.getProviderSelectionPolicy()
                .selectProvider(AuthorizationProvider.class);

        return authorizationProvider.map(provider -> provider.isUserInRole(currentSubject, role))
                .orElseGet(() -> getUser().map(Security::getRoles)
                        .orElse(CollectionsHelper.setOf())
                        .stream()
                        .anyMatch(role::equals));
    }

    @Override
    public boolean isUserInRole(String role, String authorizerName) {
        return security.resolveAtzProvider(authorizerName)
                .map(provider -> provider.isUserInRole(currentSubject, role))
                .orElse(false);
    }

    @Override
    public AuthorizationResponse authorize(Object... resource) {
        atzChecked.set(true);
        SecurityClientBuilder<AuthorizationResponse> builder = atzClientBuilder();
        for (int i = 0; i < resource.length; i++) {
            if (i == 0) {
                builder.object(resource[i]);
            }

            builder.object("object" + i, resource[i]);
        }

        return builder.buildAndGet();
    }

    @Override
    public void audit(AuditEvent event) {
        security.audit(tracingId, event);
    }

    @Override
    public void runAs(Subject subject, Runnable runnable) {
        audit(SecurityAuditEvent.info(AuditEvent.SECURITY_TYPE_PREFIX + ".runAs",
                                      "runAs(Subject,Runnable) invoked for %s")
                      .addParam(AuditEvent.AuditParam.plain("subject", subject)));

        Subject original = currentSubject;

        try {
            currentSubject = subject;
            runnable.run();
        } finally {
            currentSubject = original;
        }
    }

    @Override
    public void runAs(String role, Runnable runnable) {
        Subject currentSubject = this.currentSubject;
        Subject runAsSubject = Subject.builder()
                .principal(currentSubject.getPrincipal())
                .addGrant(Role.create(role))
                .build();

        runAs(runAsSubject, runnable);
    }

    @Override
    public Optional<Subject> getService() {
        if (serviceSubject == ANONYMOUS) {
            return Optional.empty();
        }
        return Optional.ofNullable(serviceSubject);
    }

    //only "friendly" classes may call this
    void setService(Subject serviceSubject) {
        Objects.requireNonNull(serviceSubject);
        this.serviceSubject = serviceSubject;
    }

    @Override
    public Optional<Subject> getUser() {
        if (currentSubject == ANONYMOUS) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentSubject);
    }

    //only "friendly" classes may call this
    void setUser(Subject subject) {
        Objects.requireNonNull(subject);
        this.currentSubject = subject;
    }

    @Override
    public EndpointConfig getEndpointConfig() {
        Lock lock = ecLock.readLock();
        try {
            lock.lock();
            return ec;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setEndpointConfig(EndpointConfig ec) {
        Lock lock = ecLock.writeLock();
        try {
            lock.lock();
            this.ec = ec;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SecurityEnvironment getEnv() {
        Lock rl = envLock.readLock();
        try {
            rl.lock();
            return environment;
        } finally {
            rl.unlock();
        }
    }

    @Override
    public void setEnv(SecurityEnvironment env) {
        Lock lock = envLock.writeLock();
        try {
            lock.lock();
            this.environment = env;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean atzChecked() {
        return atzChecked.get();
    }

}
