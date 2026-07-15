/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.security.AuditEvent;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpSecurityDefinitionTest {
    private static final TypeName AUDITED = TypeName.create("io.helidon.security.annotations.Audited");
    private static final String DEFAULT_AUDIT_MESSAGE_FORMAT = "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s";
    private static final Annotation TYPE_AUDIT = Annotation.builder()
            .typeName(AUDITED)
            .value("type-request")
            .property("messageFormat", "type-level audit")
            .property("okSeverity", AuditEvent.AuditSeverity.INFO)
            .property("errorSeverity", AuditEvent.AuditSeverity.ERROR)
            .build();

    @Test
    void copyRetainsAuditConfiguration() {
        HttpSecurityDefinition source = new HttpSecurityDefinition();
        source.audited(TYPE_AUDIT);

        HttpSecurityDefinition copy = source.copy();

        assertThat(copy.isAudited(), is(true));
        assertThat(copy.auditEventType(), is("type-request"));
        assertThat(copy.auditMessageFormat(), is("type-level audit"));
        assertThat(copy.auditOkSeverity(), is(AuditEvent.AuditSeverity.INFO));
        assertThat(copy.auditErrorSeverity(), is(AuditEvent.AuditSeverity.ERROR));
    }

    @Test
    void methodAuditConfigurationMergesWithTypeConfiguration() {
        HttpSecurityDefinition source = new HttpSecurityDefinition();
        source.audited(TYPE_AUDIT);

        HttpSecurityDefinition partialOverride = source.copy();
        partialOverride.audited(Annotation.builder()
                                        .typeName(AUDITED)
                                        .value("method-request")
                                        .property("messageFormat", DEFAULT_AUDIT_MESSAGE_FORMAT)
                                        .property("okSeverity", AuditEvent.AuditSeverity.SUCCESS)
                                        .property("errorSeverity", AuditEvent.AuditSeverity.FAILURE)
                                        .build());

        assertThat(partialOverride.auditEventType(), is("method-request"));
        assertThat(partialOverride.auditMessageFormat(), is("type-level audit"));
        assertThat(partialOverride.auditOkSeverity(), is(AuditEvent.AuditSeverity.INFO));
        assertThat(partialOverride.auditErrorSeverity(), is(AuditEvent.AuditSeverity.ERROR));

        HttpSecurityDefinition fullOverride = source.copy();
        fullOverride.audited(Annotation.builder()
                                     .typeName(AUDITED)
                                     .value("method-request")
                                     .property("messageFormat", "method-level audit")
                                     .property("okSeverity", AuditEvent.AuditSeverity.WARN)
                                     .property("errorSeverity", AuditEvent.AuditSeverity.AUDIT_FAILURE)
                                     .build());

        assertThat(fullOverride.auditEventType(), is("method-request"));
        assertThat(fullOverride.auditMessageFormat(), is("method-level audit"));
        assertThat(fullOverride.auditOkSeverity(), is(AuditEvent.AuditSeverity.WARN));
        assertThat(fullOverride.auditErrorSeverity(), is(AuditEvent.AuditSeverity.AUDIT_FAILURE));
    }
}
