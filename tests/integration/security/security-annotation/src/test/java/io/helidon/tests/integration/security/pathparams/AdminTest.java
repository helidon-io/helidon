/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.pathparams;

import java.util.Base64;

import io.helidon.http.Status;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.security.AuditEvent.AuditSeverity;
import io.helidon.security.spi.AuditProvider.TracedAuditEvent;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@HelidonTest
class AdminTest {

    @Test
    void testProtectedAdminEndpoint(WebTarget target) {
        testProtectedAdmin(target, "/admin");
    }

    @Test
    void testProtectedAdmin2Endpoint(WebTarget target) {
        testProtectedAdmin(target, "/admin2");
    }

    @Test
    void testUnauthorizedAdminEndpoint(WebTarget target) {
        testUnprotectedAdmin(target, "/admin/unauthorized");
    }

    @Test
    void testUnauthorizedAdmin2Endpoint(WebTarget target) {
        testUnprotectedAdmin(target, "/admin2/unauthorized");
    }

    @Test
    void testTypedMethodAnalyzerSecurity(WebTarget target) {
        testAuthenticated(target, "/admin/typed-analyzer", "typed analyzer");
    }

    @Test
    void testInterfaceMethodSecurity(WebTarget target) {
        testAuditFailure(target,
                         "/interface-security/interface-authenticated",
                         "interface-resource",
                         AuditSeverity.ERROR);
        TestAuditProvider.clear();
        testAuthenticated(target,
                          "/interface-security/interface-authenticated",
                          "interface authenticated");
        TracedAuditEvent event = TestAuditProvider.event("interface-resource");
        assertThat(event.messageFormat(), is("Interface resource request"));
        assertThat(event.severity(), is(AuditSeverity.INFO));
    }

    @Test
    void testImplementationMethodSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/interface-security/implementation-authenticated",
                          "implementation authenticated");
    }

    @Test
    void testImplementationMethodRoleSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-security/implementation-roles",
                           "implementation roles");
    }

    @Test
    void testInheritedImplementationDeclaringClassSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-security/inherited-implementation-roles",
                           "inherited implementation roles");
    }

    @Test
    void testImplementationAnalyzerRequiresAuthorization(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-security/implementation-analyzer-required",
                           "implementation analyzer required");
    }

    @Test
    void testImplementationAnalyzerForbidsAuthorization(WebTarget target) {
        testUnprotected(target,
                        "/interface-security/implementation-analyzer-forbidden",
                        "implementation analyzer forbidden");
    }

    @Test
    void testGenericInterfaceDenyAllIgnored(WebTarget target) {
        testUnprotected(target,
                        "/generic-interface-security/generic/value",
                        "value");
    }

    @Test
    void testInheritedInterfaceClassSecurity(WebTarget target) {
        ClassicAnnotationAnalyzer.reset();
        testAuthenticated(target,
                          "/inherited-interface-class-security",
                          "inherited interface authentication");
        testAuthenticated(target,
                          "/inherited-interface-class-security/authorization-disabled",
                          "inherited interface authorization disabled");

        String definitionType = "io.helidon.tests.integration.security.annotation."
                + "InheritedInterfaceClassSecurityResource";
        assertThat(ClassicAnnotationAnalyzer.analyses(),
                   contains("class:" + definitionType,
                            "reflection-method:" + definitionType + "#authenticated",
                            "reflection-method:" + definitionType + "#authorizationDisabled"));
    }

    @Test
    void testInheritedClassSecurity(WebTarget target) {
        ClassicAnnotationAnalyzer.reset();
        testAuthenticated(target,
                          "/inherited-class-security",
                          "inherited class authentication");
        testAuthenticated(target,
                          "/inherited-class-security/overridden",
                          "overridden class authentication");

        String definitionType = "io.helidon.tests.integration.security.annotation.InheritedClassSecurityResource";
        assertThat(ClassicAnnotationAnalyzer.analyses(),
                   contains("class:" + definitionType,
                            "reflection-method:" + definitionType + "#inherited",
                            "reflection-method:" + definitionType + "#overridden"));
    }

    @Test
    void testRootClassHierarchySecurity(WebTarget target) {
        testUnprotected(target,
                        "/root-class-hierarchy-security/overridden",
                        "overridden root class hierarchy");
        testDenied(target, "/root-class-hierarchy-security");
        testUnprotected(target,
                        "/root-class-hierarchy-security/open",
                        "open root class hierarchy");
    }

    @Test
    void testImplementationClassSecurity(WebTarget target) {
        testAuditFailure(target,
                         "/interface-class-security",
                         "implementation-resource",
                         AuditSeverity.AUDIT_FAILURE);
        TestAuditProvider.clear();
        testProtectedAdmin(target,
                           "/interface-class-security",
                           "definition=1");
        TracedAuditEvent event = TestAuditProvider.event("implementation-resource");
        assertThat(event.messageFormat(), is("Implementation resource request"));
        assertThat(event.severity(), is(AuditSeverity.WARN));
    }

    @Test
    void testImplementationClassWithDefinitionMethodMetadata(WebTarget target) {
        String endpoint = "/interface-class-security/class-overrides-method";
        String eventType = "definition-method-resource";

        testAuditFailure(target, endpoint, eventType, AuditSeverity.AUDIT_FAILURE);
        TestAuditProvider.clear();
        testProtectedAdmin(target,
                           endpoint,
                           "implementation class with definition method metadata");
        TracedAuditEvent event = TestAuditProvider.event(eventType);
        assertThat(event.messageFormat(), is("Implementation resource request"));
        assertThat(event.severity(), is(AuditSeverity.INFO));
    }

    @Test
    void testImplementationOwnedMethodPrecedence(WebTarget target) {
        testUnprotected(target,
                        "/interface-class-security/open",
                        "implementation method permit all");
    }

    @Test
    void testInterfaceAndOverriddenSuperclassSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-hierarchy-security",
                        "root=0, unrelated=0");
    }

    @Test
    void testReversedInterfaceHierarchySecurity(WebTarget target) {
        testUnprotected(target,
                        "/interface-hierarchy-security-reversed",
                        "reversed hierarchy");
    }

    @Test
    void testSiblingInterfaceEndpointSecurity(WebTarget target) {
        testUnprotected(target,
                        "/interface-hierarchy-security/sibling",
                        "sibling endpoint");
    }

    @Test
    void testPrivateSuperclassMethodDoesNotContributeSecurity(WebTarget target) {
        testUnprotected(target,
                        "/interface-hierarchy-security/private",
                        "private superclass");
    }

    @Test
    void testInterfaceSubresourceCustomSecurity(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/open",
                        "interface subresource");
        TestAuditProvider.clear();
        testUnprotected(target,
                        "/interface-subresource-security/sub",
                        "interface subresource");
        TracedAuditEvent event = TestAuditProvider.event("interface-subresource");
        assertThat(event.messageFormat(), is("Interface subresource request"));
        assertThat(event.severity(), is(AuditSeverity.INFO));
    }

    @Test
    void testInheritedSubresourceMethodSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/inherited",
                           "inherited subresource");
    }

    @Test
    void testInheritedSubresourceClassSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/inherited-class",
                           "inherited class subresource");
    }

    @Test
    void testBroadInheritedSubresourceClassSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/inherited-class-broad",
                           "inherited class subresource");
    }

    @Test
    void testBroadInheritedSubresourceMethodPrecedence(WebTarget target) {
        testUnprotectedForNonAdmin(target,
                                   "/interface-subresource-security/inherited-method-broad",
                                   "inherited method subresource");
    }

    @Test
    void testBroadInheritedRoleMethodPrecedence(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/inherited-role-method-broad",
                           "inherited subresource");
    }

    @Test
    void testBroadInheritedHandlerSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/inherited-handler-security/sub",
                          "inherited handler security");
    }

    @Test
    void testBroadInterfaceInheritedHandlerSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/inherited-handler-security/interface",
                          "inherited handler security");
    }

    @Test
    void testInheritedHandlerDoesNotOverrideMethodSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/inherited-handler-security/method",
                          "method security");
    }

    @Test
    void testDeclaredHandlerAuthenticationDoesNotOverrideInheritedMethodSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/inherited-handler-security/declared-authentication-disabled",
                          "declared authentication disabled handler");
    }

    @Test
    void testDeclaredHandlerAuthorizationDoesNotOverrideInheritedMethodSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/inherited-handler-security/declared-authorization-disabled",
                           "declared authorization disabled handler");
    }

    @Test
    void testInheritedLegacyAnalyzerRunsAtHandlingClassLevel(WebTarget target) {
        testUnprotected(target,
                        "/inherited-handler-security/legacy-analyzer-inherited",
                        "legacy analyzer security");
    }

    @Test
    void testDeclaredLegacyAnalyzerRunsAfterMethodWithInheritedAnnotations(WebTarget target) {
        testAuthenticated(target,
                          "/inherited-handler-security/legacy-analyzer-declared",
                          "legacy analyzer security");
    }

    @Test
    void testInheritedRoleAnalyzerSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/inherited-handler-security/role-analyzer-inherited",
                           "inherited role analyzer security");
        testUnprotected(target,
                        "/inherited-handler-security/role-analyzer-inherited/open",
                        "inherited role analyzer override");
    }

    @Test
    void testClassicAnalyzerOnImplementationOwnedSubresource(WebTarget target) {
        ClassicAnnotationAnalyzer.reset();
        testAuthenticated(target,
                          "/inherited-handler-security/classic-analyzer",
                          "classic analyzer security");
    }

    @Test
    void testClassicRootAnalyzerCacheIsolation(WebTarget target) {
        ClassicAnnotationAnalyzer.reset();
        testUnprotected(target, "/classic-root-public", "classic root security");
        testAuthenticated(target, "/classic-root-secured", "classic root security");

        String baseType = "io.helidon.tests.integration.security.annotation.ClassicRootSecurityBase";
        assertThat(ClassicAnnotationAnalyzer.analyses(),
                   contains("class:io.helidon.tests.integration.security.annotation.ClassicRootPublicResource",
                            "reflection-method:" + baseType + "#get",
                            "class:io.helidon.tests.integration.security.annotation.ClassicRootSecuredResource",
                            "reflection-method:" + baseType + "#get"));
    }

    @Test
    void testClassicSubresourceOverloadCacheIsolation(WebTarget target) {
        testUnprotected(target,
                        "/inherited-handler-security/classic-overloads/public",
                        "classic overload public");
        testAuthenticated(target,
                          "/inherited-handler-security/classic-overloads/secured",
                          "classic overload secured");
    }

    @Test
    void testNestedInterfaceSubresourceSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/nested/final",
                        "interface subresource");
    }

    @Test
    void testGenericInterfaceAncestorClassSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/generic",
                        "generic interface subresource");
    }

    @Test
    void testHandlingGenericInterfaceAncestorClassSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/handling-generic",
                        "generic interface subresource");
    }

    @Test
    void testLayeredGenericInterfaceAncestorClassSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/layered-generic",
                        "generic interface subresource");
    }

    @Test
    void testCovariantInterfaceAncestorClassSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/covariant",
                        "generic interface subresource");
    }

    @Test
    void testBroadInterfaceSubresourceSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/broad",
                        "broad interface subresource");
    }

    @Test
    void testSubresourceImplementationClassSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/implementation",
                           "implementation subresource");
    }

    @Test
    void testSubresourceImplementationMethodPrecedence(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/implementation/open",
                        "open implementation subresource");
    }

    @Test
    void testConcreteSuperclassLocatorRootSecurity(WebTarget target) {
        testProtectedAdmin(target,
                           "/interface-subresource-security/superclass-locator",
                           "concrete superclass locator");
    }

    @Test
    void testPathChildInterfaceSecurityIgnored(WebTarget target) {
        testUnprotected(target,
                        "/interface-subresource-security/path-grandparent",
                        "path grandparent subresource");
    }

    @Test
    void testConfiguredDefaultMethodSecurity(WebTarget target) {
        testAuthenticated(target,
                          "/configured-default-security",
                          "configured default");
    }

    @Test
    void testResourceClassSecurityCache(WebTarget target) {
        ClassSecurityCacheAnnotationAnalyzer.reset();

        testUnprotected(target, "/class-security-cache/first", "first");
        testUnprotected(target, "/class-security-cache/second", "second");

        assertThat(ClassSecurityCacheAnnotationAnalyzer.classAnalyses(), is(1));
        assertThat(ClassSecurityCacheAnnotationAnalyzer.methodAnalyses(), is(2));

        testUnprotected(target, "/class-security-cache/first", "first");
        testUnprotected(target, "/class-security-cache/second", "second");

        assertThat(ClassSecurityCacheAnnotationAnalyzer.classAnalyses(), is(1));
        assertThat(ClassSecurityCacheAnnotationAnalyzer.methodAnalyses(), is(2));
    }

    private void testAuditFailure(WebTarget target,
                                  String endpoint,
                                  String eventType,
                                  AuditSeverity severity) {
        TestAuditProvider.clear();
        try (Response response = target.path(endpoint)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
        }
        assertThat(TestAuditProvider.event(eventType).severity(), is(severity));
    }

    private void testProtectedAdmin(WebTarget target, String endpoint) {
        testProtectedAdmin(target, endpoint, "admin");
    }

    private void testProtectedAdmin(WebTarget target, String endpoint, String entity) {
        try (Response response = target.path(endpoint)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.FORBIDDEN_403.code()));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is(entity));
        }
    }

    private void testDenied(WebTarget target, String endpoint) {
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.FORBIDDEN_403.code()));
        }
    }

    private void testAuthenticated(WebTarget target, String endpoint, String entity) {
        try (Response response = target.path(endpoint)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("unknown"))
                .get()) {
            assertThat(response.getStatus(), is(Status.UNAUTHORIZED_401.code()));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is(entity));
        }
    }

    private void testUnprotectedAdmin(WebTarget target, String endpoint) {
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("unauthorized admin"));
        }
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("success"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is("unauthorized admin"));
        }
    }

    private void testUnprotected(WebTarget target, String endpoint, String entity) {
        try (Response response = target.path(endpoint)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is(entity));
        }
    }

    private void testUnprotectedForNonAdmin(WebTarget target, String endpoint, String entity) {
        testUnprotected(target, endpoint, entity);
        try (Response response = target.path(endpoint)
                .request()
                .header("Authorization", basic("fail"))
                .get()) {
            assertThat(response.getStatus(), is(Status.OK_200.code()));
            assertThat(response.readEntity(String.class), is(entity));
        }
    }

    private String basic(String user) {
        String uap = user + ":password";
        return "basic " + Base64.getEncoder().encodeToString(uap.getBytes());
    }
}
