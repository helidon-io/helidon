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

package io.helidon.security.examples.spi;

import java.util.List;
import java.util.stream.Collectors;

import io.helidon.security.AuditEvent;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.spi.AuditProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Auditer}.
 */
public class AuditerTest {
    @Test
    public void integrateIt() throws InterruptedException {
        Auditer auditer = new Auditer();

        Security sec = Security.builder()
                .addAuthorizationProvider(new AtzProviderSync())
                .addAuditProvider(auditer)
                .build();

        SecurityContext context = sec.createContext("unit-test");
        context.env(SecurityEnvironment.builder()
                               .path("/public/path"));

        AuthorizationResponse response = context.authorize();

        // as auditing is asynchronous, we must give it some time to process
        Thread.sleep(100);

        List<AuditProvider.TracedAuditEvent> messages = auditer.getMessages();
        // there should be two messages - configuration of security and authorization

        List<AuditProvider.TracedAuditEvent> atzEvents = messages.stream()
                .filter(event -> event.eventType().startsWith(AuditEvent.AUTHZ_TYPE_PREFIX))
                .collect(Collectors.toList());

        assertThat("We only expect a single authorization event", atzEvents.size(), is(1));

    }
}
