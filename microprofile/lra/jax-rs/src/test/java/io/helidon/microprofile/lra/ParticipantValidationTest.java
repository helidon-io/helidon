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
import java.util.Map;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.lra.coordinator.client.Participant;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

            try (SeContainer ignored = assertDoesNotThrow(initializer::initialize)) {
                // Container started without configuring a callback secret.
            }
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
                try (SeContainer ignored = initializer.initialize()) {
                    // Unexpected successful startup is closed before the assertion fails.
                }
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
                try (SeContainer ignored = initializer.initialize()) {
                    // Unexpected successful startup is closed before the assertion fails.
                }
            });

            assertThat("Unexpected message for " + invalidUri,
                       e.getMessage(),
                       containsString("absolute HTTP or HTTPS URI"));
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jaxRsParticipantDoesNotCacheRequestDerivedBaseUri() {
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
                    .addBeanClasses(ValidJaxRsResource.class);

            try (SeContainer container = assertDoesNotThrow(initializer::initialize)) {
                ParticipantService participantService = container.select(ParticipantService.class).get();
                URI firstLraId = URI.create("https://coordinator.example/lra/first");
                URI secondLraId = URI.create("https://coordinator.example/lra/second");
                Participant first = participantService.participant(URI.create("https://first.example"),
                                                                   ValidJaxRsResource.class,
                                                                   firstLraId);
                Participant second = participantService.participant(URI.create("https://second.example"),
                                                                    ValidJaxRsResource.class,
                                                                    secondLraId);

                assertAll(
                        () -> assertThat(first.compensate().orElseThrow().getHost(), is("first.example")),
                        () -> assertThat(second.compensate().orElseThrow().getHost(), is("second.example"))
                );
            }
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryJaxRsParticipantRetainsConfiguredUriCompatibility() {
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

            try (SeContainer ignored = assertDoesNotThrow(initializer::initialize)) {
                // Existing JAX-RS-only configuration remains accepted when canonical identity is not required.
            }
        } finally {
            configResolver.releaseConfig(config);
        }
    }

    @Test
    void jaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsLeaveParticipantRequiresCanonicalUri(ValidJaxRsLeaveResource.class);
    }

    @Test
    void interfaceInheritedJaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsLeaveParticipantRequiresCanonicalUri(InterfaceInheritedJaxRsLeaveResource.class);
    }

    @Test
    void superclassInheritedJaxRsLeaveParticipantRequiresCanonicalUri() {
        assertJaxRsLeaveParticipantRequiresCanonicalUri(SuperclassInheritedJaxRsLeaveResource.class);
    }

    @SuppressWarnings("unchecked")
    private void assertJaxRsLeaveParticipantRequiresCanonicalUri(Class<?> beanClass) {
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
                    .addBeanClasses(beanClass);

            DeploymentException e = assertThrows(DeploymentException.class, () -> {
                try (SeContainer ignored = initializer.initialize()) {
                    // Unexpected successful startup is closed before the assertion fails.
                }
            });

            assertThat(e.getMessage(), containsString(ParticipantService.CONFIG_PARTICIPANT_URL_KEY));
        } finally {
            configResolver.releaseConfig(config);
        }
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

    interface LeaveResourceContract {

        @Leave
        Response leave(URI lraId);
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
