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

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class TestDefaults {
    static final String PROPERTY_NAME = "helidon-junit-extension-test-property";
    static final String DEFAULT_VALUE = "this-should-not-be-in-config";

    @Inject
    private BeanManager beanManager;

    @Inject
    @ConfigProperty(name = PROPERTY_NAME, defaultValue = DEFAULT_VALUE)
    private String shouldNotExist;

    private static boolean beforeAllCalled;
    private boolean beforeEachCalled;

    @BeforeAll
    static void initClass() {
        beforeAllCalled = true;
    }

    @BeforeEach
    void beforeEach() {
        beforeEachCalled = true;
    }

    @Test
    void testIt() {
        assertThat(beanManager, notNullValue());
        assertThat(shouldNotExist, is(DEFAULT_VALUE));
    }

    @Test
    void testLifecycleMethodsCalled() {
        // this is to validate we can still use the usual junit methods
        assertThat("Before all should have been called", beforeAllCalled, is(true));
        assertThat("Before each should have been called", beforeEachCalled, is(true));
    }
}