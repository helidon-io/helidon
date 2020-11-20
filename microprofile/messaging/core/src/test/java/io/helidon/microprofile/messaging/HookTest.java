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
 *
 */

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.inject.Inject;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.hook.BasicHookBean;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

@HelidonTest
@DisableDiscovery
@AddBean(BasicHookBean.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(MessagingCdiExtension.class)
public class HookTest {

    @Inject
    MessagingCdiExtension messagingCdiExtension;

    AtomicLong idSeq = new AtomicLong();

    private void makeConnections(@Observes @Priority(PLATFORM_AFTER + 80) @Initialized(ApplicationScoped.class) Object event,
                                 BeanManager beanManager) {
        System.out.println("registering");
        messagingCdiExtension.registerMessagingMethodInvocationHook((method, message) -> {
            MessageContext.lookup(message).put("txId", idSeq.incrementAndGet());
            Long txId = (Long) MessageContext.lookup(message).get("txId");
            System.out.println("TxId:" + txId + " Invoking method " + method.getName());
        }, (method, o) -> {
            System.out.println("output "+o);
            Long txId = (Long) MessageContext.lookup((Message<?>) o).get("txId");
            System.out.println("TxId:" + txId + " Invoked method " + method.getName());
        });
    }

    @Test
    void name() {

    }
}
