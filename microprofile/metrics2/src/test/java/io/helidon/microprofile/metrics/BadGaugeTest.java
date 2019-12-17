/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import org.hamcrest.Matchers;
import org.jboss.weld.exceptions.DeploymentException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BadGaugeTest  {

    // TODO - remove following Disabled line once MP metrics enforces restriction
    @org.junit.jupiter.api.Disabled
    @Test
    public void testBadBean() {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, Matchers.is(Matchers.notNullValue()));
        initializer.addBeanClasses(BadGaugedBean.class);
        DeploymentException ex = assertThrows(DeploymentException.class, () -> {
            SeContainer cdiContainer = initializer.initialize();
            cdiContainer.close();
        });

        Throwable cause = ex.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, is(instanceOf(IllegalArgumentException.class)));

        assertThat(cause.getMessage(), containsString("Error processing @Gauge"));
        assertThat(cause.getMessage(), containsString(BadGaugedBean.class.getName() + ":notAllowed"));

        Throwable subCause = cause.getCause();
        assertThat(subCause, notNullValue());
        assertThat(subCause, is(instanceOf(IllegalArgumentException.class)));
        assertThat(subCause.getMessage(), containsString("assignment-compatible with Number"));

    }
}
