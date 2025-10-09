/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.common.types.Annotation;
import io.helidon.security.AuditEvent;
import io.helidon.security.SecurityLevel;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer.Flag;

class HttpSecurityDefinition {
    private final Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> analyzerResponses = new IdentityHashMap<>();
    private final List<SecurityLevel> securityLevels = new ArrayList<>();

    private boolean requiresAuthentication;
    private boolean failOnFailureIfOptional;
    private boolean authnOptional;
    private boolean authorizeByDefault = true;
    private boolean atzExplicit;
    private String authenticator;
    private String authorizer;
    private boolean audited;
    private String auditEventType;
    private String auditMessageFormat;
    private AuditEvent.AuditSeverity auditOkSeverity;
    private AuditEvent.AuditSeverity auditErrorSeverity;
    private Boolean requiresAuthorization;

    String authorizer() {
        return authorizer;
    }

    boolean requiresAuthorization() {
        if (null != requiresAuthorization) {
            return requiresAuthorization;
        }

        int count = 0;
        for (SecurityLevel securityLevel : securityLevels) {
            count += securityLevel.annotations().size();
        }
        return (count != 0) || authorizeByDefault;
    }

    Optional<String> authenticator() {
        return Optional.ofNullable(authenticator);
    }

    boolean authenticationOptional() {
        return authnOptional;
    }

    String auditMessageFormat() {
        return auditMessageFormat;
    }

    String auditEventType() {
        return auditEventType;
    }

    AuditEvent.AuditSeverity auditErrorSeverity() {
        return auditErrorSeverity;
    }

    AuditEvent.AuditSeverity auditOkSeverity() {
        return auditOkSeverity;
    }

    boolean isAudited() {
        return audited;
    }

    boolean noSecurity() {
        boolean authorize = requiresAuthorization != null && requiresAuthorization;
        boolean authenticate = requiresAuthentication;
        boolean audit = audited;
        return !authenticate && !authorize && !audit && !authorizeByDefault;
    }

    AnnotationAnalyzer.AnalyzerResponse analyzerResponse(AnnotationAnalyzer analyzer) {
        return analyzerResponses.get(analyzer);
    }

    void addSecurityLevel(SecurityLevel securityLevel) {
        securityLevels.add(securityLevel);
    }

    SecurityLevel lastSecurityLevel() {
        return securityLevels.getLast();
    }

    void lastSecurityLevel(SecurityLevel securityLevel) {
        securityLevels.set(securityLevels.size() - 1, securityLevel);
    }

    HttpSecurityDefinition copy() {
        HttpSecurityDefinition result = new HttpSecurityDefinition();
        result.requiresAuthentication = this.requiresAuthentication;
        result.requiresAuthorization = this.requiresAuthorization;
        result.failOnFailureIfOptional = this.failOnFailureIfOptional;
        result.authnOptional = this.authnOptional;
        result.authenticator = this.authenticator;
        result.authorizer = this.authorizer;
        result.securityLevels.addAll(this.securityLevels);
        result.authorizeByDefault = this.authorizeByDefault;
        result.atzExplicit = this.atzExplicit;

        return result;
    }

    void analyzerResponse(AnnotationAnalyzer analyzer, AnnotationAnalyzer.AnalyzerResponse response) {
        analyzerResponses.put(analyzer, response);

        switch (response.authenticationResponse()) {
        case Flag.REQUIRED -> {
            requiresAuthentication = true;
            authnOptional = false;
        }
        case Flag.OPTIONAL -> {
            requiresAuthentication = true;
            authnOptional = true;
        }
        case Flag.FORBIDDEN -> {
            requiresAuthentication = false;
            authnOptional = false;
        }
        default -> {
        }
        }

        if (this.requiresAuthorization == null) {
            this.requiresAuthorization = switch (response.authorizationResponse()) {
                case Flag.REQUIRED, Flag.OPTIONAL -> true;
                case Flag.FORBIDDEN -> false;
                default -> null;
            };
        }

        this.authenticator = response.authenticator().orElse(this.authenticator);
        this.authorizer = response.authorizer().orElse(this.authorizer);
    }

    List<SecurityLevel> securityLevels() {
        return securityLevels;
    }

    void authenticated(Annotation annotation) {
        this.requiresAuthentication = annotation.booleanValue().orElse(true);
        this.authenticator = annotation.stringValue("provider")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);
        this.authnOptional = annotation.booleanValue("optional").orElse(false);
    }

    void authorized(Annotation annotation) {
        this.requiresAuthorization = annotation.booleanValue().orElse(true);
        this.authorizer = annotation.stringValue("provider")
                .filter(Predicate.not(String::isBlank))
                .orElse(null);
        this.atzExplicit = annotation.booleanValue("explicit").orElse(false);
    }

    void audited(Annotation annotation) {
        this.audited = true;
        this.auditEventType = checkDefault(auditEventType,
                                           annotation.stringValue(),
                                           "request");
        this.auditMessageFormat = checkDefault(auditMessageFormat,
                                               annotation.stringValue("messageFormat"),
                                               "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s");
        this.auditOkSeverity = checkDefault(auditOkSeverity,
                                            annotation.enumValue("okSeverity", AuditEvent.AuditSeverity.class),
                                            AuditEvent.AuditSeverity.SUCCESS);
        this.auditErrorSeverity = checkDefault(auditErrorSeverity,
                                               annotation.enumValue("errorSeverity", AuditEvent.AuditSeverity.class),
                                               AuditEvent.AuditSeverity.FAILURE);
    }

    void failOnFailureIfOptional(boolean failOnFailureIfOptional) {
        this.failOnFailureIfOptional = failOnFailureIfOptional;
    }

    boolean failOnFailureIfOptional() {
        return failOnFailureIfOptional;
    }

    void authorizeAnnotatedOnly(boolean annotatedOnly) {
        this.authorizeByDefault = !annotatedOnly;
    }

    boolean requiresAuthentication() {
        return requiresAuthentication;
    }

    void requiresAuthentication(boolean requires) {
        this.requiresAuthentication = requires;
    }

    boolean atzExplicit() {
        return atzExplicit;
    }

    private <T> T checkDefault(T currentValue, Optional<T> annotValue, T defaultValue) {
        if (null == currentValue) {
            return annotValue.orElse(defaultValue);
        }

        if (currentValue.equals(defaultValue)) {
            return annotValue.orElse(defaultValue);
        }

        return currentValue;
    }
}
