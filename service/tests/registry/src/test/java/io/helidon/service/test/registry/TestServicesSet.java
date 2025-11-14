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

package io.helidon.service.test.registry;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class TestServicesSet {

    @Test
    void testServicesSet() {
        Services.set(Child.class, new ChildImpl("custom"));

        assertThat(Services.get(Child.class).message(), is("custom"));
        assertThat(Services.get(TopLevel.class).message(), is("custom"));

    }

    interface TopLevel {
        String message();
    }

    interface Child extends TopLevel {
    }

    @Service.Singleton
    static class ChildImpl implements Child {
        private final String message;

        @Service.Inject
        ChildImpl() {
            this.message = "injected";
        }

        ChildImpl(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
