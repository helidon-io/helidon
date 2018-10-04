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

package io.helidon.microprofile.faulttolerance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Class FallbackTest.
 */
public class FallbackTest extends FaultToleranceTest {

    @Test
    public void testFallback() {
        FallbackBean bean = newBean(FallbackBean.class);
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallback();
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    public void testFallbackHandler() {
        FallbackBean bean = newBean(FallbackBean.class);
        assertThat(bean.getCalled(), is(false));
        String value = bean.fallbackHandler("someValue");
        assertThat(bean.getCalled(), is(true));
        assertThat(value, is("fallbackHandler"));
    }
}
