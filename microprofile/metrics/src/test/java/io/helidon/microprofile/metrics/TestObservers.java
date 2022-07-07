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
package io.helidon.microprofile.metrics;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@HelidonTest
public class TestObservers {

    @Test
    void testDiscoveryObserver() {
        TestDiscoveryObserverImpl observer = TestDiscoveryObserverImpl.Provider.instance();
        assertThat("Observer's discoveries", observer.discoveries().size(),  is(not(0)));
    }

    @Test
    void testRegistrationObserver() {
        TestRegistrationObserverImpl observer = TestRegistrationObserverImpl.Provider.instance();
        assertThat("Observer's registrations", observer.registrations(), is(not(0)));
    }
}
