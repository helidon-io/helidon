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

package io.helidon.security.abac.policy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityContext;
import io.helidon.security.abac.policy.spi.PolicyExecutor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link PolicyValidator}.
 */
public class PolicyValidatorTest {
    @Test
    public void testSimplePolicyExecutor() {
        PolicyValidator pv = PolicyValidator.builder()
                .addExecutor(new MyPolicyExecutor())
                .build();

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.isAuthenticated()).thenReturn(true);

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(sc);
        when(request.getObject()).thenReturn(Optional.empty());

        PolicyValidator.PolicyConfig pc = PolicyValidator.PolicyConfig.builder()
                .statement(PolicyClass.class.getName() + ".isAuthenticated")
                .build();
        Errors.Collector collector = Errors.collector();
        pv.validate(pc, collector, request);

        collector.collect().checkValid();
    }

    @Test
    public void testSimplePolicyExecutorFail() {
        PolicyValidator pv = PolicyValidator.builder()
                .addExecutor(new MyPolicyExecutor())
                .build();

        SecurityContext sc = mock(SecurityContext.class);
        when(sc.isAuthenticated()).thenReturn(false);

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(sc);
        when(request.getObject()).thenReturn(Optional.empty());

        PolicyValidator.PolicyConfig pc = PolicyValidator.PolicyConfig.builder()
                .statement(PolicyClass.class.getName() + ".isAuthenticated")
                .build();
        Errors.Collector collector = Errors.collector();
        pv.validate(pc, collector, request);

        if (collector.collect().isValid()) {
            fail("Policy evaluation should have failed, as user is not authenticated");
        }
    }

    private class MyPolicyExecutor implements PolicyExecutor {
        @Override
        public void executePolicy(String policyStatement, Errors.Collector collector, ProviderRequest request) {
            // policy is fully qualified class name and a method. The method signature must be (collector, request)
            int lastDot = policyStatement.lastIndexOf('.');
            if (lastDot > 0) {
                String className = policyStatement.substring(0, lastDot);
                String methodName = policyStatement.substring(lastDot + 1);

                try {
                    Object object = instantiate(className);
                    Method method = object.getClass().getMethod(methodName, Errors.Collector.class, ProviderRequest.class);
                    method.invoke(null, collector, request);
                } catch (Exception e) {
                    collector.fatal("Failed to execute policy object method. Class: " + className + ", method: " + methodName +
                                            ", error message: " + e.getMessage() + ", exception: " + e);
                }
            } else {
                collector.fatal("Invalid policy statement: " + policyStatement);
            }
        }

        private Object instantiate(String className)
                throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
                InstantiationException {
            return Class.forName(className).getConstructor().newInstance();
        }
    }
}
