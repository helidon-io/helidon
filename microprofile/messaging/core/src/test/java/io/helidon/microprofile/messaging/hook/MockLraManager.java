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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.helidon.microprofile.messaging.MessageContext;
import io.helidon.microprofile.messaging.MessagingMethod;

import org.eclipse.microprofile.reactive.messaging.Message;

public class MockLraManager {

    private final List<Method> compensateMethods;
    AtomicLong idSeq = new AtomicLong();

    static final String TX_KEY = "txId";

    Map<Long, Message<?>> txMap = new ConcurrentHashMap<>();
    private Object beanInstance;

    public MockLraManager(Object beanInstance) {
        this.beanInstance = beanInstance;
        Class<?> beanClass = beanInstance.getClass();
        compensateMethods = Arrays.stream(beanClass.getDeclaredMethods())
                .filter(m -> m.getAnnotation(MockCompensate.class) != null)
                .collect(Collectors.toList());
    }

    public void releaseMessageReferences(){
        txMap.clear();
    }

    static Optional<Long> getTxId(Message<?> msg) {
        return Optional.ofNullable(MessageContext.lookup(msg).get(TX_KEY)).map(o -> (Long) o);
    }

    long checkOrCreateTx(MessagingMethod method, Object o) {
        MockLRA lra = method.getMethod().getAnnotation(MockLRA.class);

        if (lra == null) {
            return -1;
        }

        if (!(o instanceof Message)) {
            return -1;
        }

        Message<?> message = (Message<?>) o;

        if (lra.value() == MockLRA.Type.NEW) {
            Long txId = (Long) MessageContext.lookup(message).get(TX_KEY);
            if (txId == null) {
                txId = idSeq.incrementAndGet();
                MessageContext.lookup(message).put(TX_KEY, txId);
                txMap.put(txId, message);
            }
        }

        if (lra.value() == MockLRA.Type.REQUIRED) {
            Long txId = (Long) MessageContext.lookup(message).get(TX_KEY);
            if (txId == null) {
                throw new RuntimeException("No tx context found!");
            }
            return txId;
        }

        return -1;
    }

    public void compensate(MessagingMethod method, Message<?> message, Throwable t) {
        Object[] params = new Object[] {message, t};
        for (Method m : compensateMethods) {
            int parameterCount = m.getParameterCount();
            if (0 < parameterCount && parameterCount <= 2) {
                try {
                    m.invoke(beanInstance, Arrays.copyOfRange(params, 0, parameterCount));
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
