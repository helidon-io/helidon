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

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.jboss.weld.exceptions.DefinitionException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticipantValidationTest {

    @Test
    @SuppressWarnings("unchecked")
    void invalidNonJaxRsStatusSignatureFailsDeployment() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp.initializer.allow", "true")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(LraCdiExtension.class)
                    .addBeanClasses(InvalidStatusResource.class);

            DefinitionException e = assertThrows(DefinitionException.class, initializer::initialize);

            assertThat(e.getCause(), instanceOf(DeploymentException.class));
            assertThat(e.getMessage(), containsString("First argument of LRA method"));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void abstractNonJaxRsParticipantDoesNotRequireSecret() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp.initializer.allow", "true")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addBeanClasses(AbstractStatusResource.class);

            SeContainer container = initializer.initialize();
            container.close();
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void concreteNonJaxRsParticipantRequiresSecret() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        ParticipantService.CONFIG_PARTICIPANT_URL_KEY, "http://localhost")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addBeanClasses(ValidStatusResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, initializer::initialize);

            assertThat(e.getMessage(), containsString(NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void interfaceDeclaredNonJaxRsParticipantRequiresSecret() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        ParticipantService.CONFIG_PARTICIPANT_URL_KEY, "http://localhost")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addBeanClasses(InterfaceOnlyNonJaxRsParticipant.class,
                                    InterfaceOnlyNonJaxRsResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, initializer::initialize);

            assertThat(e.getMessage(), containsString(NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void concreteNonJaxRsParticipantRequiresCanonicalUri() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "server.port", "0",
                        NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY, ParticipantTest.TEST_CALLBACK_SECRET)))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(ValidStatusResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, () -> {
                SeContainer container = initializer.initialize();
                container.close();
            });

            assertThat(e.getMessage(), containsString(ParticipantService.CONFIG_PARTICIPANT_URL_KEY));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    void concreteNonJaxRsParticipantRejectsInvalidCanonicalUris() {
        assertInvalidCanonicalUri("http:participant");
        assertInvalidCanonicalUri("ftp://participant.example");
        assertInvalidCanonicalUri("http://user@participant.example");
        assertInvalidCanonicalUri("http://participant.example?query=value");
        assertInvalidCanonicalUri("http://participant.example#fragment");
    }

    @Test
    void jaxRsParticipantRejectsInvalidEndpointConfiguration() {
        String endpointKey = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".test";
        String firstEndpointKey = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".first";
        String secondEndpointKey = ParticipantService.CONFIG_PARTICIPANT_ENDPOINTS_KEY + ".second";
        assertInvalidEndpointConfiguration(
                Map.of(endpointKey + ".application-path", "/application",
                       endpointKey + ".url", "https://participant.example/application"),
                endpointKey + ".listener is required");
        assertInvalidEndpointConfiguration(
                Map.of(endpointKey + ".listener", "@default",
                       endpointKey + ".application-path", "application",
                       endpointKey + ".url", "https://participant.example/application"),
                "application-path must start with '/'");
        assertInvalidEndpointConfiguration(
                Map.of(endpointKey + ".listener", "@default",
                       endpointKey + ".application-path", "/application",
                       endpointKey + ".url", "https://participant.example/application",
                       endpointKey + ".unknown", "value"),
                "Unknown configuration property");
        assertInvalidEndpointConfiguration(
                Map.of(firstEndpointKey + ".listener", "@default",
                       firstEndpointKey + ".application-path", "/application",
                       firstEndpointKey + ".url",
                       "https://participant.example/application",
                       secondEndpointKey + ".listener", "@default",
                       secondEndpointKey + ".application-path", "/application/",
                       secondEndpointKey + ".url",
                       "https://participant.example/application"),
                "Duplicate LRA participant endpoint");
        assertInvalidEndpointConfiguration(
                Map.of(firstEndpointKey + ".listener", "@default",
                       firstEndpointKey + ".application-path", "/encoded%21",
                       firstEndpointKey + ".url",
                       "https://participant.example/encoded",
                       secondEndpointKey + ".listener", "@default",
                       secondEndpointKey + ".application-path", "/encoded!",
                       secondEndpointKey + ".url",
                       "https://participant.example/encoded"),
                "Duplicate LRA participant endpoint");
    }

    @SuppressWarnings("unchecked")
    private void assertInvalidCanonicalUri(String invalidUri) {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "server.port", "0",
                        ParticipantService.CONFIG_PARTICIPANT_URL_KEY, invalidUri,
                        NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY, ParticipantTest.TEST_CALLBACK_SECRET)))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(ValidStatusResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, () -> {
                SeContainer container = initializer.initialize();
                container.close();
            });

            assertThat("Unexpected message for " + invalidUri,
                       e.getMessage(),
                       containsString("absolute HTTP or HTTPS URI"));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @SuppressWarnings("unchecked")
    private void assertInvalidEndpointConfiguration(Map<String, String> endpointProperties, String expectedMessage) {
        Map<String, String> properties = new HashMap<>(endpointProperties);
        properties.put("mp.initializer.allow", "true");
        properties.put("server.port", "0");
        properties.put(ParticipantService.CONFIG_PARTICIPANT_URL_KEY, "https://participant.example/application");

        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(properties))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(ValidJaxRsResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, initializer::initialize);

            assertThat(e.getMessage(), containsString(expectedMessage));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    void jaxRsParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(ValidJaxRsResource.class);
    }

    @Test
    void inheritedJaxRsParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(InheritedJaxRsResource.class);
    }

    @Test
    void diamondInheritedJaxRsParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(DiamondInheritedJaxRsResource.class, DiamondParticipant.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void jaxRsResourceWithoutParticipantCallbacksDoesNotRequireCanonicalUri() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "server.port", "0")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(NonParticipantResource.class);

            SeContainer container = initializer.initialize();
            container.close();
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryJaxRsParticipantRejectsInvalidCanonicalUri() {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "server.port", "0",
                        ParticipantService.CONFIG_PARTICIPANT_URL_KEY, "ftp://participant.example")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(ValidJaxRsResource.class);

            DeploymentException e = assertThrows(DeploymentException.class, () -> {
                SeContainer container = initializer.initialize();
                container.close();
            });

            assertThat(e.getMessage(), containsString("absolute HTTP or HTTPS URI"));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    void jaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(ValidJaxRsLeaveResource.class);
    }

    @Test
    void interfaceInheritedJaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(InterfaceInheritedJaxRsLeaveResource.class);
    }

    @Test
    void superclassInheritedJaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsParticipantRequiresCanonicalUri(SuperclassInheritedJaxRsLeaveResource.class);
    }

    @SuppressWarnings("unchecked")
    private void assertJaxRsParticipantRequiresCanonicalUri(Class<?>... beanClasses) {
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true",
                        "server.port", "0")))
                .build();
        configResolver.registerConfig(config, Thread.currentThread().getContextClassLoader());

        try {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addExtensions(ConfigCdiExtension.class)
                    .addExtensions(LraCdiExtension.class)
                    .addExtensions(ServerCdiExtension.class)
                    .addExtensions(JaxRsCdiExtension.class)
                    .addExtensions(CdiComponentProvider.class)
                    .addBeanClasses(beanClasses);

            DeploymentException e = assertThrows(DeploymentException.class, () -> {
                SeContainer container = initializer.initialize();
                container.close();
            });

            assertThat(e.getMessage(), containsString(ParticipantService.CONFIG_PARTICIPANT_URL_KEY));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    interface DiamondParticipant {

        @Compensate
        Response compensate(URI lraId);
    }

    interface LeftDiamondParticipant extends DiamondParticipant {
    }

    interface RightDiamondParticipant extends DiamondParticipant {
    }

    interface LeaveResourceContract {

        @Leave
        Response leave(URI lraId);
    }

    interface InterfaceOnlyNonJaxRsParticipant {

        @Compensate
        Response compensate(URI lraId);
    }

    @ApplicationScoped
    static class InvalidStatusResource {

        @Status
        ParticipantStatus status(String lraId) {
            return ParticipantStatus.Active;
        }
    }

    @ApplicationScoped
    abstract static class AbstractStatusResource {

        @Status
        abstract ParticipantStatus status(URI lraId);
    }

    @ApplicationScoped
    static class ValidStatusResource {

        @Compensate
        ParticipantStatus compensate(URI lraId) {
            return ParticipantStatus.Compensated;
        }

        @Status
        ParticipantStatus status(URI lraId) {
            return ParticipantStatus.Active;
        }
    }

    @ApplicationScoped
    static class InterfaceOnlyNonJaxRsResource implements InterfaceOnlyNonJaxRsParticipant {

        @Override
        public Response compensate(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/valid-jax-rs")
    @ApplicationScoped
    static class ValidJaxRsResource {

        @PUT
        @Path("/compensate")
        @Compensate
        Response compensate(URI lraId) {
            return Response.ok().build();
        }
    }

    static class InheritedParticipant {

        @Compensate
        public Response compensate(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/inherited-jax-rs")
    @ApplicationScoped
    static class InheritedJaxRsResource extends InheritedParticipant {

        @Override
        @PUT
        @Path("/compensate")
        public Response compensate(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/diamond-inherited-jax-rs")
    @ApplicationScoped
    static class DiamondInheritedJaxRsResource implements LeftDiamondParticipant, RightDiamondParticipant {

        @Override
        @PUT
        @Path("/compensate")
        public Response compensate(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/non-participant")
    @ApplicationScoped
    static class NonParticipantResource {

        @PUT
        Response invoke() {
            return Response.ok().build();
        }
    }

    @Path("/valid-jax-rs-leave")
    @ApplicationScoped
    static class ValidJaxRsLeaveResource {

        @PUT
        @Path("/compensate")
        @Compensate
        Response compensate(URI lraId) {
            return Response.ok().build();
        }

        @PUT
        @Path("/leave")
        @Leave
        Response leave(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/interface-inherited-jax-rs-leave")
    @ApplicationScoped
    static class InterfaceInheritedJaxRsLeaveResource implements LeaveResourceContract {

        @PUT
        @Path("/compensate")
        @Compensate
        Response compensate(URI lraId) {
            return Response.ok().build();
        }

        @Override
        @PUT
        @Path("/leave")
        public Response leave(URI lraId) {
            return Response.ok().build();
        }
    }

    static class LeaveResourceBase {

        @Leave
        public Response leave(URI lraId) {
            return Response.ok().build();
        }
    }

    @Path("/superclass-inherited-jax-rs-leave")
    @ApplicationScoped
    static class SuperclassInheritedJaxRsLeaveResource extends LeaveResourceBase {

        @PUT
        @Path("/compensate")
        @Compensate
        Response compensate(URI lraId) {
            return Response.ok().build();
        }

        @Override
        @PUT
        @Path("/leave")
        public Response leave(URI lraId) {
            return Response.ok().build();
        }
    }
}
