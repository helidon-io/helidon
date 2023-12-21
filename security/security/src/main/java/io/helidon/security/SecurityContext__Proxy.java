package io.helidon.security;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

class SecurityContext__Proxy implements SecurityContext {
    private final Supplier<SecurityContext> scopedSupplier;

    SecurityContext__Proxy(Supplier<SecurityContext> scopedSupplier) {
        this.scopedSupplier = scopedSupplier;
    }

    @Override
    public boolean isAuthorized() {
        return scopedSupplier.get()
                .isAuthorized();
    }

    @Override
    public SecurityRequestBuilder<?> securityRequestBuilder() {
        return scopedSupplier.get()
                .securityRequestBuilder();
    }

    @Override
    public SecurityRequestBuilder<?> securityRequestBuilder(SecurityEnvironment environment) {
        return scopedSupplier.get()
                .securityRequestBuilder(environment);
    }

    @Override
    public SecurityClientBuilder<AuthenticationResponse> atnClientBuilder() {
        return null;
    }

    @Override
    public AuthenticationResponse authenticate() {
        return null;
    }

    @Override
    public SecurityClientBuilder<AuthorizationResponse> atzClientBuilder() {
        return null;
    }

    @Override
    public OutboundSecurityClientBuilder outboundClientBuilder() {
        return null;
    }

    @Override
    public AuthorizationResponse authorize(Object... resource) {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public void logout() {

    }

    @Override
    public boolean isUserInRole(String role, String authorizerName) {
        return false;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public void audit(AuditEvent event) {

    }

    @Override
    public Optional<Subject> service() {
        return Optional.empty();
    }

    @Override
    public Optional<Principal> servicePrincipal() {
        return SecurityContext.super.servicePrincipal();
    }

    @Override
    public String serviceName() {
        return SecurityContext.super.serviceName();
    }

    @Override
    public Optional<Subject> user() {
        return Optional.empty();
    }

    @Override
    public Optional<Principal> userPrincipal() {
        return SecurityContext.super.userPrincipal();
    }

    @Override
    public String userName() {
        return SecurityContext.super.userName();
    }

    @Override
    public void runAs(Subject subject, Runnable runnable) {

    }

    @Override
    public void runAs(String role, Runnable runnable) {

    }

    @Override
    public SpanContext tracingSpan() {
        return null;
    }

    @Override
    public Tracer tracer() {
        return null;
    }

    @Override
    public String id() {
        return null;
    }

    @Override
    public SecurityTime serverTime() {
        return null;
    }

    @Override
    public SecurityEnvironment env() {
        return null;
    }

    @Override
    public void env(Supplier<SecurityEnvironment> envBuilder) {
        SecurityContext.super.env(envBuilder);
    }

    @Override
    public void env(SecurityEnvironment env) {

    }

    @Override
    public EndpointConfig endpointConfig() {
        return null;
    }

    @Override
    public void endpointConfig(EndpointConfig ec) {

    }

    @Override
    public void endpointConfig(Supplier<EndpointConfig> epBuilder) {
        SecurityContext.super.endpointConfig(epBuilder);
    }

    @Override
    public boolean atzChecked() {
        return SecurityContext.super.atzChecked();
    }
}
