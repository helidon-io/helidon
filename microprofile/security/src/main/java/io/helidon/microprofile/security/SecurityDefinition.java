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

package io.helidon.microprofile.security;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.security.AuditEvent;
import io.helidon.security.SecurityLevel;
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;
import io.helidon.security.providers.common.spi.AnnotationAnalyzer;

/**
 * Definition of security for one a method, resource class or application class.
 */
class SecurityDefinition {

    private final Map<AnnotationAnalyzer, AnnotationAnalyzer.AnalyzerResponse> analyzerResponses = new IdentityHashMap<>();
    private final List<SecurityLevel> securityLevels = new ArrayList<>();

    /*
     * True if authentication is needed to execute.
     */
    private boolean requiresAuthentication;
    private boolean failOnFailureIfOptional;
    private boolean authnOptional;
    private boolean authorizeByDefault;
    private boolean atzExplicit;
    private String authenticator;
    private String authorizer;
    private boolean audited;
    private String auditEventType;
    private String auditMessageFormat;
    private AuditEvent.AuditSeverity auditOkSeverity;
    private AuditEvent.AuditSeverity auditErrorSeverity;
    private Boolean requiresAuthorization;

    private SecurityDefinition() {
    }

    SecurityDefinition(boolean authorizeAnnotatedOnly, boolean failOnFailureIfOptional) {
        this.authorizeByDefault = !authorizeAnnotatedOnly;
        this.failOnFailureIfOptional = failOnFailureIfOptional;
    }

    @Override
    public String toString() {
        return "SecurityDefinition{"
                + "analyzerResponses=" + analyzerResponses
                + ", securityLevels=" + securityLevels
                + ", requiresAuthentication=" + requiresAuthentication
                + ", failOnFailureIfOptional=" + failOnFailureIfOptional
                + ", authnOptional=" + authnOptional
                + ", authorizeByDefault=" + authorizeByDefault
                + ", atzExplicit=" + atzExplicit
                + ", authenticator='" + authenticator + '\''
                + ", authorizer='" + authorizer + '\''
                + ", audited=" + audited
                + ", auditEventType='" + auditEventType + '\''
                + ", auditMessageFormat='" + auditMessageFormat + '\''
                + ", auditOkSeverity=" + auditOkSeverity
                + ", auditErrorSeverity=" + auditErrorSeverity
                + ", requiresAuthorization=" + requiresAuthorization
                + '}';
    }

    SecurityDefinition copyMe() {
        SecurityDefinition result = new SecurityDefinition();
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

    void fromConfig(Config config) {
        config.get("authorize").as(Boolean.class).ifPresent(this::requiresAuthorization);
        config.get("authorizer").as(String.class).ifPresent(this::authorizer);
        config.get("authorization-explicit").as(Boolean.class).ifPresent(this::atzExplicit);
        config.get("authenticate").as(Boolean.class).ifPresent(this::requiresAuthentication);
        config.get("authenticator").as(String.class).ifPresent(this::authenticator);
        config.get("authentication-optional").as(Boolean.class).ifPresent(this::authenticationOptional);
        config.get("audit").as(Boolean.class).ifPresent(this::audited);
        config.get("audit-event-type").as(String.class).ifPresent(this::auditEventType);
        config.get("audit-message-format").as(String.class).ifPresent(this::auditMessageFormat);
        config.get("audit-ok-severity").as(String.class).ifPresent(this::auditOkSeverity);
        config.get("audit-error-severity").as(String.class).ifPresent(this::auditErrorSeverity);
    }

    void add(Authenticated atn) {
        if (null == atn) {
            return;
        }
        this.requiresAuthentication = atn.value();
        this.authnOptional = atn.optional();
        this.authenticator = "".equals(atn.provider()) ? null : atn.provider();
    }

    void add(Authorized atz) {
        if (null == atz) {
            return;
        }
        this.requiresAuthorization = atz.value();
        this.authorizer = "".equals(atz.provider()) ? null : atz.provider();
        this.atzExplicit = atz.explicit();
    }

    void add(Audited audited) {
        if (null == audited) {
            return;
        }
        this.audited = true;
        this.auditEventType = checkDefault(auditEventType, audited.value(), Audited.DEFAULT_EVENT_TYPE);
        this.auditMessageFormat = checkDefault(auditMessageFormat, audited.messageFormat(), Audited.DEFAULT_MESSAGE_FORMAT);
        this.auditOkSeverity = checkDefault(auditOkSeverity, audited.okSeverity(), Audited.DEFAULT_OK_SEVERITY);
        this.auditErrorSeverity = checkDefault(auditErrorSeverity, audited.errorSeverity(), Audited.DEFAULT_ERROR_SEVERITY);
    }

    void requiresAuthentication(boolean atn) {
        this.requiresAuthentication = atn;
    }

    void requiresAuthorization(boolean atz) {
        this.requiresAuthorization = atz;
    }

    private <T> T checkDefault(T currentValue, T annotValue, T defaultValue) {
        if (null == currentValue) {
            return annotValue;
        }

        if (currentValue.equals(defaultValue)) {
            return annotValue;
        }

        return currentValue;
    }

    boolean requiresAuthentication() {
        return requiresAuthentication;
    }

    boolean authenticationOptional() {
        return authnOptional;
    }

    void authenticationOptional(boolean authnOptional) {
        this.authnOptional = authnOptional;
    }

    boolean failOnFailureIfOptional() {
        return failOnFailureIfOptional;
    }

    void failOnFailureIfOptional(boolean failOnFailureIfOptional) {
        this.failOnFailureIfOptional = failOnFailureIfOptional;
    }

    boolean requiresAuthorization() {
        if (null != requiresAuthorization) {
            return requiresAuthorization;
        }

        int count = 0;
        for (SecurityLevel securityLevel : securityLevels) {
            count += securityLevel.getClassLevelAnnotations().size();
            count += securityLevel.getMethodLevelAnnotations().size();
        }
        return (count != 0) || authorizeByDefault;
    }

    boolean atzExplicit() {
        return atzExplicit;
    }

    void atzExplicit(boolean atzExplicit) {
        this.atzExplicit = atzExplicit;
    }

    String authenticator() {
        return authenticator;
    }

    void authenticator(String authenticator) {
        this.authenticator = authenticator;
    }

    String authorizer() {
        return authorizer;
    }

    void authorizer(String authorizer) {
        this.authorizer = authorizer;
    }

    List<SecurityLevel> securityLevels() {
        return securityLevels;
    }

    boolean audited() {
        return audited;
    }

    void audited(boolean audited) {
        this.audited = audited;
    }

    String auditEventType() {
        return auditEventType;
    }

    void auditEventType(String auditEventType) {
        this.auditEventType = auditEventType;
    }

    String auditMessageFormat() {
        return auditMessageFormat;
    }

    void auditMessageFormat(String auditMessageFormat) {
        this.auditMessageFormat = auditMessageFormat;
    }

    AuditEvent.AuditSeverity auditOkSeverity() {
        return auditOkSeverity;
    }

    void auditOkSeverity(AuditEvent.AuditSeverity auditOkSeverity) {
        this.auditOkSeverity = auditOkSeverity;
    }

    private void auditOkSeverity(String severity) {
        auditOkSeverity(AuditEvent.AuditSeverity.valueOf(severity));
    }

    AuditEvent.AuditSeverity auditErrorSeverity() {
        return auditErrorSeverity;
    }


    void auditErrorSeverity(AuditEvent.AuditSeverity auditOkSeverity) {
        this.auditErrorSeverity = auditOkSeverity;
    }

    private void auditErrorSeverity(String severity) {
        auditErrorSeverity(AuditEvent.AuditSeverity.valueOf(severity));
    }

    AnnotationAnalyzer.AnalyzerResponse analyzerResponse(AnnotationAnalyzer analyzer) {
        return analyzerResponses.get(analyzer);
    }

    void analyzerResponse(AnnotationAnalyzer analyzer, AnnotationAnalyzer.AnalyzerResponse analyzerResponse) {
        analyzerResponses.put(analyzer, analyzerResponse);

        switch (analyzerResponse.authenticationResponse()) {
            case REQUIRED -> {
                requiresAuthentication = true;
                authnOptional = false;
            }
            case OPTIONAL -> {
                requiresAuthentication = true;
                authnOptional = true;
            }
            case FORBIDDEN -> {
                requiresAuthentication = false;
                authnOptional = false;
            }
            default -> {}
        }

        if (this.requiresAuthorization == null) {
            this.requiresAuthorization = switch (analyzerResponse.authorizationResponse()) {
                case REQUIRED, OPTIONAL -> true;
                case FORBIDDEN -> false;
                default -> null;
            };
        }

        this.authenticator = analyzerResponse.authenticator().orElse(this.authenticator);
        this.authorizer = analyzerResponse.authorizer().orElse(this.authorizer);
    }
}
