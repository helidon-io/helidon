/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi.impl;

import io.helidon.pico.InjectionPointInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Tests for {@link io.helidon.pico.spi.impl.DefaultInjectionPointInfo}.
 */
public class DefaultInjectionPointInfoTest {

    @Test
    public void toFieldIdentity() {
        assertThat(DefaultInjectionPointInfo
                           .toFieldIdentity("fieldName",
                                            InjectionPointInfo.Access.PACKAGE_PRIVATE, null),
                   is("fieldName"));
        assertThat(DefaultInjectionPointInfo
                           .toFieldIdentity("fieldName",
                                            InjectionPointInfo.Access.PUBLIC, () -> "packageName"),
                   is("packageName.fieldName"));
        assertThat(DefaultInjectionPointInfo
                           .toFieldIdentity("fieldName",
                                            InjectionPointInfo.Access.PROTECTED, () -> "packageName"),
                   is("packageName.fieldName"));
        assertThat(DefaultInjectionPointInfo
                           .toFieldIdentity("fieldName",
                                            InjectionPointInfo.Access.PRIVATE, () -> "packageName"),
                   is("packageName.fieldName"));
        assertThat(DefaultInjectionPointInfo
                           .toFieldIdentity("fieldName",
                                            InjectionPointInfo.Access.PACKAGE_PRIVATE, () -> "packageName"),
                   is("packageName.fieldName"));
    }

    @Test
    public void toMethodBaseIdentity() {
        assertThat(DefaultInjectionPointInfo
                           .toMethodBaseIdentity("methodName", 5,
                                                 InjectionPointInfo.Access.PUBLIC, null),
                   is("methodName|5"));
        assertThat(DefaultInjectionPointInfo
                           .toMethodBaseIdentity("methodName", 5,
                                                 InjectionPointInfo.Access.PUBLIC, () -> "packageName"),
                   is("methodName|5"));
        assertThat(DefaultInjectionPointInfo
                           .toMethodBaseIdentity("methodName", 5,
                                                 InjectionPointInfo.Access.PACKAGE_PRIVATE, () -> "packageName"),
                   is("packageName.methodName|5"));
        assertThat(DefaultInjectionPointInfo
                           .toMethodBaseIdentity(InjectionPointInfo.CTOR, 1,
                                                 InjectionPointInfo.Access.PACKAGE_PRIVATE, () -> "packageName"),
                   is("packageName.<init>|1"));
    }

    @Test
    public void identityAndBaseIdentityOfInjectionPointInfo() {
        InjectionPointInfo ipInfo = DefaultInjectionPointInfo.builder()
                .elementName("methodName")
                .elementKind(InjectionPointInfo.ElementKind.METHOD)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("methodName|0"));
        assertThat(ipInfo.identity(),
                   is("methodName|0"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .elementName("methodName")
                .elementArgs(5)
                .elementKind(InjectionPointInfo.ElementKind.METHOD)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("methodName|5"));
        assertThat(ipInfo.identity(),
                   is("methodName|5"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .elementName("methodName")
                .elementArgs(5)
                .elementKind(InjectionPointInfo.ElementKind.METHOD)
                .access(InjectionPointInfo.Access.PACKAGE_PRIVATE)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.methodName|5"));
        assertThat(ipInfo.identity(),
                   is("packageName.methodName|5"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .elementName("methodName")
                .elementArgs(5)
                .elementOffset(1)
                .elementKind(InjectionPointInfo.ElementKind.METHOD)
                .access(InjectionPointInfo.Access.PACKAGE_PRIVATE)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.methodName|5"));
        assertThat(ipInfo.identity(),
                   is("packageName.methodName|5(1)"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .elementArgs(5)
                .elementOffset(1)
                .access(InjectionPointInfo.Access.PACKAGE_PRIVATE)
                .elementKind(InjectionPointInfo.ElementKind.CTOR)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("<init>|5"));
        assertThat(ipInfo.identity(),
                   is("<init>|5(1)"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .elementArgs(5)
                .elementOffset(1)
                .access(InjectionPointInfo.Access.PUBLIC)
                .elementKind(InjectionPointInfo.ElementKind.CTOR)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.<init>|5"));
        assertThat(ipInfo.identity(),
                   is("packageName.<init>|5(1)"));
    }

    @Test
    public void identityAndBaseIdentity_fields() {
        InjectionPointInfo ipInfo = DefaultInjectionPointInfo.builder()
                .elementName("fieldName")
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("fieldName"));
        assertThat(ipInfo.identity(),
                   is("fieldName"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .elementName("fieldName")
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .elementArgs(1)
                .elementOffset(0)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("fieldName"));
        assertThat(ipInfo.identity(),
                   is("fieldName"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .elementName("fieldName")
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .elementArgs(1)
                .elementOffset(0)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.fieldName"));
        assertThat(ipInfo.identity(),
                   is("packageName.fieldName"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .access(InjectionPointInfo.Access.PACKAGE_PRIVATE)
                .elementName("fieldName")
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .elementArgs(1)
                .elementOffset(0)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.fieldName"));
        assertThat(ipInfo.identity(),
                   is("packageName.fieldName"));

        ipInfo = DefaultInjectionPointInfo.builder()
                .serviceTypeName("packageName.ServiceTypeName")
                .access(InjectionPointInfo.Access.PROTECTED)
                .elementName("fieldName")
                .elementKind(InjectionPointInfo.ElementKind.FIELD)
                .elementArgs(1)
                .elementOffset(0)
                .build();
        assertThat(ipInfo.baseIdentity(),
                   is("packageName.fieldName"));
        assertThat(ipInfo.identity(),
                   is("packageName.fieldName"));
    }

    @Test
    public void toPackageName() {
        assertThat(DefaultInjectionPointInfo.toPackageName("a.b.c.ClassName").get(), is("a.b.c"));
        assertThat(DefaultInjectionPointInfo.toPackageName("a.b.c.ClassName.Builder").get(), is("a.b.c"));
        assertThat(DefaultInjectionPointInfo.toPackageName("a.b.c.ClassName.Builder.Type").get(), is("a.b.c"));
        assertThat(DefaultInjectionPointInfo.toPackageName("a.b.c.myclass").get(), is("a.b.c"));
    }
}
