/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    SecurityDefinition(boolean authorizeAnnotatedOnly) {
        this.authorizeByDefault = !authorizeAnnotatedOnly;
    }

    SecurityDefinition copyMe() {
        SecurityDefinition result = new SecurityDefinition();
        result.requiresAuthentication = this.requiresAuthentication;
        result.authnOptional = this.authnOptional;
        result.authenticator = this.authenticator;
        result.authorizer = this.authorizer;
        result.securityLevels.addAll(this.securityLevels);
        result.authorizeByDefault = this.authorizeByDefault;
        result.atzExplicit = this.atzExplicit;

        return result;
    }

    public void add(Authenticated atn) {
        if (null == atn) {
            return;
        }
        this.requiresAuthentication = atn.value();
        this.authnOptional = atn.optional();
        this.authenticator = "".equals(atn.provider()) ? null : atn.provider();
    }

    public void add(Authorized atz) {
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

    void setRequiresAuthorization(boolean atz) {
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

    public boolean isAtzExplicit() {
        return atzExplicit;
    }

    String getAuthenticator() {
        return authenticator;
    }

    String getAuthorizer() {
        return authorizer;
    }

    public List<SecurityLevel> getSecurityLevels() {
        return securityLevels;
    }

    public boolean isAudited() {
        return audited;
    }

    public String getAuditEventType() {
        return auditEventType;
    }

    public String getAuditMessageFormat() {
        return auditMessageFormat;
    }

    public AuditEvent.AuditSeverity getAuditOkSeverity() {
        return auditOkSeverity;
    }

    public AuditEvent.AuditSeverity getAuditErrorSeverity() {
        return auditErrorSeverity;
    }

    public AnnotationAnalyzer.AnalyzerResponse analyzerResponse(AnnotationAnalyzer analyzer) {
        return analyzerResponses.get(analyzer);
    }

    public void analyzerResponse(AnnotationAnalyzer analyzer, AnnotationAnalyzer.AnalyzerResponse analyzerResponse) {
        analyzerResponses.put(analyzer, analyzerResponse);

        switch (analyzerResponse.authenticationResponse()) {
        case REQUIRED:
            requiresAuthentication = true;
            authnOptional = false;
            break;
        case OPTIONAL:
            requiresAuthentication = true;
            authnOptional = true;
            break;
        case FORBIDDEN:
            requiresAuthentication = false;
            authnOptional = false;
            break;
        case ABSTAIN:
        default:
            break;
        }

        switch (analyzerResponse.authorizationResponse()) {
        case REQUIRED:
            requiresAuthorization = true;
            break;
        case OPTIONAL:
            requiresAuthorization = true;
            break;
        case FORBIDDEN:
            requiresAuthorization = false;
            break;
        case ABSTAIN:
        default:
            break;
        }

        this.authenticator = analyzerResponse.authenticator().orElse(this.authenticator);
        this.authorizer = analyzerResponse.authorizer().orElse(this.authorizer);
    }
}
