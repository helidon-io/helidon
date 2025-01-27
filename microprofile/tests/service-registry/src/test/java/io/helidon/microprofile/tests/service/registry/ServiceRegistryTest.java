/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.service.registry;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
public class ServiceRegistryTest {

    @Inject
    CdiBean myBean;
    @Inject
    MyContract myContract;

    @Test
    public void testInterceptorsWorkOnRegistryContractThroughBean() {
        MyInterceptor.MESSAGES.clear();

        String message = myBean.message();

        assertThat(message, is("MyService"));
        assertThat(MyInterceptor.MESSAGES, hasItem("MyService"));
    }

    @Test
    public void testInterceptorsWorkOnRegistryContract() {
        MyInterceptor.MESSAGES.clear();

        String message = myContract.message();

        assertThat(message, is("MyService"));
        assertThat(MyInterceptor.MESSAGES, hasItem("MyService"));
    }

    // sanity check
    @Test
    public void testInterceptorsWorkOnCdiBean() {
        MyInterceptor.MESSAGES.clear();

        myBean.hello();
        assertThat(MyInterceptor.MESSAGES, hasItems("hello"));
    }

}
