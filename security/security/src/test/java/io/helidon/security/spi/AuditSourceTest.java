/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.security.spi;

import java.util.Optional;
import java.util.OptionalInt;

import io.helidon.security.spi.AuditProvider.AuditSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test audit source.
 */
public class AuditSourceTest {
    @Test
    void testLocation() {
        // DO NOT MOVE THIS LINE - if you add an import, this test will fail - or fix assertion of line number!!!
        AuditSource auditSource = AuditSource.build();

        assertThat(auditSource.getClassName(), is(Optional.of(AuditSourceTest.class.getName())));
        assertThat(auditSource.getFileName(), is(Optional.of("AuditSourceTest.java")));
        assertThat(auditSource.getMethodName(), is(Optional.of("testLocation")));
        assertThat(auditSource.getLineNumber(), is(OptionalInt.of(35)));
    }

    @Test
    void testSecurityClassees() {
        StackTraceElement el = new StackTraceElement("io.helidon.security.AuthenticationClientImpl",
                                                     "lambda$authenticate$6",
                                                     "AuthenticationClientImpl.java",
                                                     118);
        assertThat("Should be a security class", AuditSource.isSecurityClass(el), is(true));

    }
}
