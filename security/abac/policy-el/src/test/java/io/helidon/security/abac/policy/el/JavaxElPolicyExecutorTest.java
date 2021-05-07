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

package io.helidon.security.abac.policy.el;

import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link JavaxElPolicyExecutor}.
 */
public class JavaxElPolicyExecutorTest {
    @Test
    public void testSimpleExpression() {
        JavaxElPolicyExecutor ex = JavaxElPolicyExecutor.create();

        SecurityEnvironment env = SecurityEnvironment.create();
        Subject user = Subject.builder()
                .principal(Principal.create("unit-test-user"))
                .addGrant(Role.create("unit-test-user-role"))
                .build();
        Subject service = Subject.builder()
                .principal(Principal.create("unit-test-service"))
                .addGrant(Role.create("unit-test-service-role"))
                .build();
        MyResource object = new MyResource("unit-test-user");

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.service()).thenReturn(Optional.of(service));
        when(request.subject()).thenReturn(Optional.of(user));
        when(request.env()).thenReturn(env);
        when(request.getObject()).thenReturn(Optional.of(object));

        Errors.Collector collector = Errors.collector();
        ex.executePolicy("${user.principal.id == object.owner}", collector, request);
        collector.collect().checkValid();

        collector = Errors.collector();
        ex.executePolicy("${user.principal.id == object.owner}", collector, request);
        collector.collect().checkValid();

        collector = Errors.collector();
        ex.executePolicy("${inRole(user, 'unit-test-user-role') && inRole(service, 'unit-test-service-role')}",
                         collector,
                         request);
        collector.collect().checkValid();

        collector = Errors.collector();
        ex.executePolicy("${service.principal.id == object.owner}", collector, request);
        if (collector.collect().isValid()) {
            fail("Should have failed, as service is not the owner of the object");
        }

        collector = Errors.collector();
        ex.executePolicy("${env.time.year >= 2017}",
                         collector,
                         request);
        collector.collect().checkValid();
    }

    // bean must be public, as otherwise EL cannot access properties
    public static class MyResource {
        private String owner;

        public MyResource(String owner) {
            this.owner = owner;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }
    }
}
