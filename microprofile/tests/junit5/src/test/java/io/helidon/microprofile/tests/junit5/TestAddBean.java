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

package io.helidon.microprofile.tests.junit5;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.tests.junit5.TestDefaults.DEFAULT_VALUE;
import static io.helidon.microprofile.tests.junit5.TestDefaults.PROPERTY_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(TestAddBean.MyBean.class)
class TestAddBean {
    @Inject
    private MyBean myBean;

    @Test
    void testIt() {
        assertThat(myBean, notNullValue());
        assertThat(myBean.configured(), is(DEFAULT_VALUE));
    }

    static class MyBean {
        private final String configured;

        @Inject
        MyBean(@ConfigProperty(name = PROPERTY_NAME, defaultValue = DEFAULT_VALUE) String configured) {
            this.configured = configured;
        }

        String configured() {
            return configured;
        }
    }
}