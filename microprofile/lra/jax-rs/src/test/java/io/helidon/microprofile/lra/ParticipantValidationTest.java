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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.jboss.weld.exceptions.DefinitionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticipantValidationTest {

    private ConfigProviderResolver configResolver;
    private ClassLoader classLoader;
    private Config originalConfig;
    private Config config;

    @BeforeEach
    void registerConfig() {
        configResolver = ConfigProviderResolver.instance();
        classLoader = Thread.currentThread().getContextClassLoader();
        originalConfig = configResolver.getConfig(classLoader);
        config = configResolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp.initializer.allow", "true")))
                .build();
        configResolver.registerConfig(config, classLoader);
    }

    @AfterEach
    void restoreConfig() {
        configResolver.releaseConfig(config);
        if (originalConfig != null) {
            configResolver.registerConfig(originalConfig, classLoader);
        }
    }

    @Test
    void invalidNonJaxRsStatusSignatureFailsDeployment() {
        assertDeploymentFails(InvalidStatusResource.class, "First argument of LRA method");
    }

    @Test
    void invalidInheritedNonJaxRsStatusSignatureFailsDeployment() {
        assertDeploymentFails(InvalidInheritedStatusResource.class, "First argument of LRA method");
    }

    @Test
    void invalidAfterLraSecondArgumentFailsDeployment() {
        assertDeploymentFails(InvalidAfterLraResource.class, "Second argument of LRA method");
    }

    @Test
    void invalidNonJaxRsStatusSecondArgumentFailsDeployment() {
        assertDeploymentFails(InvalidStatusParentResource.class, "Second argument of LRA method");
    }

    @SuppressWarnings("unchecked")
    private void assertDeploymentFails(Class<?> resourceClass, String message) {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(LraCdiExtension.class)
                .addBeanClasses(resourceClass);

        DefinitionException e = assertThrows(DefinitionException.class, initializer::initialize);

        assertThat(e.getMessage(), containsString(message));
    }

    @ApplicationScoped
    static class InvalidStatusResource {

        @Status
        ParticipantStatus status(String lraId) {
            return ParticipantStatus.Active;
        }
    }

    @ApplicationScoped
    static class InvalidStatusParentResource {

        @Status
        ParticipantStatus status(URI lraId, String parentId) {
            return ParticipantStatus.Active;
        }
    }

    interface InvalidStatusContract {

        @Status
        ParticipantStatus status(String lraId);
    }

    @ApplicationScoped
    static class InvalidInheritedStatusResource implements InvalidStatusContract {

        @LRA
        void start() {
        }

        @Override
        public ParticipantStatus status(String lraId) {
            return ParticipantStatus.Active;
        }
    }

    @ApplicationScoped
    static class InvalidAfterLraResource {

        @AfterLRA
        void afterLra(URI lraId, URI parentId) {
        }
    }
}
