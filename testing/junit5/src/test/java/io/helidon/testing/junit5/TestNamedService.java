/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.testing.junit5;

import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
class TestNamedService {

    @Test
    void byName(@Service.Named("bean-a") BeanParent beanA,
                @Service.Named("bean-b") BeanParent beanB) {
        assertThat(beanA.getName(), is("bean-a"));
        assertThat(beanB.getName(), is("bean-b"));
    }

    @Test
    void byType(@Service.Named("bean-a") BeanA beanA,
                @Service.Named("bean-b") BeanB beanB) {
        assertThat(beanA.getName(), is("bean-a"));
        assertThat(beanB.getName(), is("bean-b"));
    }


    @Service.Singleton
    @Service.Named("bean-a")
    static class BeanA implements BeanParent {
        @Override
        public String getName() {
            return "bean-a";
        }
    }

    @Service.Singleton
    @Service.Named("bean-b")
    static class BeanB implements BeanParent {
        @Override
        public String getName() {
            return "bean-b";
        }
    }

    interface BeanParent {
        String getName();
    }
}
