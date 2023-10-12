/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import io.helidon.microprofile.testing.junit5.AddBean;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test fallback methods and handlers.
 */
@AddBean(FallbackBean.class)
class FallbackTest extends FaultToleranceTest {

    @Inject
    private FallbackBean bean;

    @Override
    void reset() {
        bean.reset();
    }

    @Test
    void testFallback() {
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallback();
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    void testFallbackBase() {
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallbackBase();
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    void testFallbackHandler() {
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallbackHandler("someValue");
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallbackHandler"));
    }

    @Test
    void testFallbackHandler2() {
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallbackHandler2("someValue");
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallbackHandler2"));
    }
}
