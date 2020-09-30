/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi;

import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@HelidonTest
class MicronautCdiExtensionTest {
    @Inject
    private CdiBean cdiBean;
    @Inject
    private BothBean bothBean;
    @Inject
    private MicronautBean micronautBean;
    @Inject
    private CdiWithInjected injected;

    @Test
    void testCdiInterceptor() {
        assertAll(
                () -> assertThat(cdiBean.cdiAnnotated(), is("CdiBean.cdiAnnotated.cdi")),
                () -> assertThat(bothBean.cdiAnnotated(), is("BothBean.cdiAnnotated.cdi")),
                // CDI interceptors on micronaut beans are not expected to work
                () -> assertThat(micronautBean.cdiAnnotated(), is("MicronautBean.cdiAnnotated"))
        );

    }

    @Test
    void testBothInterceptors() {
        assertAll(
                () -> assertThat(cdiBean.bothAnnotated(), is("CdiBean.bothAnnotated.cdi.µ")),
                () -> assertThat(bothBean.bothAnnotated(), is("BothBean.bothAnnotated.cdi.µ")),
                // CDI interceptors on micronaut beans are not expected to work
                () -> assertThat(micronautBean.bothAnnotated(), is("MicronautBean.bothAnnotated.µ"))
        );
    }

    @Test
    void testMicronautInterceptors() {
        assertAll(
                () -> assertThat(cdiBean.µAnnotated(), is("CdiBean.µAnnotated.µ")),
                () -> assertThat(bothBean.µAnnotated(), is("BothBean.µAnnotated.µ")),
                // CDI interceptors on micronaut beans are not expected to work
                () -> assertThat(micronautBean.µAnnotated(), is("MicronautBean.µAnnotated.µ"))
        );
    }

    @Test
    void testInjected() {
        TestBean bean = injected.cdiBean();
        assertThat(bean, notNullValue());
        assertThat(bean.bothAnnotated(), is("CdiBean.bothAnnotated.cdi.µ"));

        bean = injected.bothBean();
        assertThat(bean, notNullValue());
        assertThat(bean.bothAnnotated(), is("BothBean.bothAnnotated.cdi.µ"));

        bean = injected.micronautBean();
        assertThat(bean, notNullValue());
        assertThat(bean.bothAnnotated(), is("MicronautBean.bothAnnotated.µ"));
    }
}