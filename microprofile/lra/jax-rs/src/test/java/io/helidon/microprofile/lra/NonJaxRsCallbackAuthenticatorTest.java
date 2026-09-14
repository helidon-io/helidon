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
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.inject.spi.DeploymentException;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NonJaxRsCallbackAuthenticatorTest {

    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final URI LRA_ID = URI.create("http://localhost/lra-coordinator/first");
    private static final String CALLBACK_TYPE = "compensate";
    private static final String CLASS_NAME = CallbackResource.class.getName();
    private static final String METHOD_NAME = "compensate";

    @Test
    void strictModeRequiresCapabilityForExactCallback() {
        NonJaxRsCallbackAuthenticator authenticator = authenticator(false);
        String capability = authenticator.capability(LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME);

        assertThat(authenticator.authenticate(List.of(capability),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(true));
        assertThat(authenticator.authenticate(List.of(), LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME), is(false));
        assertThat(authenticator.authenticate(List.of(capability, capability),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(false));
        assertThat(authenticator.authenticate(List.of(capability),
                                              URI.create("http://localhost/lra-coordinator/second"),
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(false));
        assertThat(authenticator.authenticate(List.of(capability),
                                              LRA_ID,
                                              "complete",
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(false));
        assertThat(authenticator.authenticate(List.of(capability),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              OtherCallbackResource.class.getName(),
                                              METHOD_NAME),
                   is(false));
        assertThat(authenticator.authenticate(List.of(capability),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              "otherMethod"),
                   is(false));
        assertThat(authenticator.authenticate(List.of("invalid"),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(false));
    }

    @Test
    void compatibilityModeUsesUnsignedCallbacks() {
        NonJaxRsCallbackAuthenticator authenticator = authenticator(true);
        ParticipantImpl participant = new ParticipantFactory(URI.create("http://localhost:8080"),
                                                             NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                                                             CallbackResource.class)
                .participant(LRA_ID, authenticator);

        assertThat(participant.compensate().orElseThrow().getRawQuery(), nullValue());
        assertThat(authenticator.authenticate(List.of(), LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME), is(true));
        assertThat(authenticator.authenticate(List.of("invalid"),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(false));

        String capability = authenticator.capability(LRA_ID, CALLBACK_TYPE, CLASS_NAME, METHOD_NAME);
        assertThat(authenticator.authenticate(List.of(capability),
                                              LRA_ID,
                                              CALLBACK_TYPE,
                                              CLASS_NAME,
                                              METHOD_NAME),
                   is(true));
    }

    @Test
    void strictModeAddsCapabilityToParticipantCallback() {
        NonJaxRsCallbackAuthenticator authenticator = authenticator(false);
        ParticipantImpl participant = new ParticipantFactory(URI.create("http://localhost:8080"),
                                                             NonJaxRsResource.CONTEXT_PATH_DEFAULT,
                                                             CallbackResource.class)
                .participant(LRA_ID, authenticator);

        assertThat(participant.compensate().orElseThrow().getRawQuery(),
                   containsString(NonJaxRsCallbackAuthenticator.CAPABILITY_QUERY_PARAMETER + "=v1."));
    }

    @Test
    void secretMustBePresentAndStronglyEncoded() {
        NonJaxRsCallbackAuthenticator missing = new NonJaxRsCallbackAuthenticator(Optional.empty(), false);

        DeploymentException missingException = assertThrows(DeploymentException.class, missing::validateConfiguration);
        assertThat(missingException.getMessage(), containsString(NonJaxRsCallbackAuthenticator.CONFIG_SECRET_KEY));
        assertThrows(DeploymentException.class,
                     () -> new NonJaxRsCallbackAuthenticator(Optional.of("not-base64!"), false));
        assertThrows(DeploymentException.class,
                     () -> new NonJaxRsCallbackAuthenticator(Optional.of("c2hvcnQ="), false));
        assertDoesNotThrow(() -> authenticator(false).validateConfiguration());
    }

    private static NonJaxRsCallbackAuthenticator authenticator(boolean compatibilityMode) {
        return new NonJaxRsCallbackAuthenticator(Optional.of(TEST_SECRET), compatibilityMode);
    }

    static class CallbackResource {

        @Compensate
        public void compensate(URI lraId) {
        }
    }

    static class OtherCallbackResource {
    }
}
