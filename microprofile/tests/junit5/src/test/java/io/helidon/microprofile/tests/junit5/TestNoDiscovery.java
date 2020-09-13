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

import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import io.helidon.microprofile.config.ConfigCdiExtension;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest(discovery = false)
@AddBean(TestNoDiscovery.MyBean.class)
class TestNoDiscovery {
    @Inject
    private MyBean myBean;

    @Test
    void testIt() {
        assertThat(myBean, notNullValue());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> CDI.current().getBeanManager()
                                                                  .getExtension(ConfigCdiExtension.class),
                                                          "Config extension should not be loaded, as we have disabled discovery");

        assertThat("Message should contain the extension name",
                   exception.getMessage(),
                   containsString(ConfigCdiExtension.class.getName()));
    }

    public static class MyBean {
    }
}