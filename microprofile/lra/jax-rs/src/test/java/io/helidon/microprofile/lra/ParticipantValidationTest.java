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

import java.util.Map;

import io.helidon.config.mp.MpConfigSources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.jboss.weld.exceptions.DefinitionException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
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

    @ApplicationScoped
    static class InvalidStatusResource {

        @Status
        ParticipantStatus status(String lraId) {
            return ParticipantStatus.Active;
        }
    }
}
