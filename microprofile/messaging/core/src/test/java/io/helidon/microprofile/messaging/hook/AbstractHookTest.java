/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging.hook;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
public abstract class AbstractHookTest {

    @Inject
    MessagingCdiExtension messagingCdiExtension;

    MockLraManager lraManager = new MockLraManager(this);
    List<String> TEST_DATA = List.of("test1", "test2", "test3");
    List<String> actual = new LinkedList<>();

    private void makeConnections(@Observes @Priority(PLATFORM_AFTER -1) @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        messagingCdiExtension.beforeMethodInvocation(lraManager::checkOrCreateTx);
        messagingCdiExtension.afterMethodInvocation(lraManager::checkOrCreateTx);
        messagingCdiExtension.onMethodInvocationFailure(lraManager::compensate);
    }

    protected void addActual(Message<?> msg){
        actual.add(MockLraManager.getTxId(msg).get() + "#" + msg.getPayload());
    }

    protected List<String> expected(){
        return lraManager.txMap
                .entrySet().stream()
                .map(e -> e.getKey() + "#" + e.getValue().getPayload())
                .collect(Collectors.toList());
    }

    @Test
    void run() {
        MatcherAssert.assertThat(actual, IsEqual.equalTo(expected()));
    }

}
