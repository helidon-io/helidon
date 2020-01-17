/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.tyrus;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.microprofile.cdi.HelidonContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Class WebSocketExtensionTest.
 */
public class WebSocketCdiExtensionTest {

    private static SeContainer cdiContainer;

    @BeforeAll
    public static void startCdiContainer() {
        cdiContainer = HelidonContainer.instance().start();
    }

    @AfterAll
    public static void shutDownCdiContainer() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }

    private WebSocketApplication webSocketApplication() {
        BeanManager beanManager = cdiContainer.getBeanManager();
        WebSocketCdiExtension extension = beanManager.getExtension(WebSocketCdiExtension.class);
        return extension.toWebSocketApplication().build();
    }

    @Test
    public void testExtension() {
        WebSocketApplication application = webSocketApplication();
        assertThat(application.endpointClasses().size(), is(greaterThan(0)));
        assertThat(application.endpointConfigs().size(), is(0));
    }
}
