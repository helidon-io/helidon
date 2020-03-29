/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test audit source.
 */
public class AuditSourceTest {
    @Test
    void testLocation() {
        // DO NOT MOVE THIS LINE - if you add an import, this test will fail - or fix assertion of line number!!!
        AuditSource auditSource = AuditSource.create();

        assertThat(auditSource.className(), is(Optional.of(AuditSourceTest.class.getName())));
        assertThat(auditSource.fileName(), is(Optional.of("AuditSourceTest.java")));
        assertThat(auditSource.methodName(), is(Optional.of("testLocation")));
        assertThat(auditSource.lineNumber(), is(OptionalInt.of(37)));
    }

    @Test
    void testSecurityClasses() {
        StackWalker.StackFrame el = mock(StackWalker.StackFrame.class);

        when(el.getClassName()).thenReturn("io.helidon.security.AuthenticationClientImpl");
        when(el.getMethodName()).thenReturn("lambda$authenticate$6");
        when(el.getFileName()).thenReturn("AuthenticationClientImpl.java");
        when(el.getLineNumber()).thenReturn(118);

        assertThat("Should be a security class", AuditSource.isSecurityClass(el), is(true));

    }
}
