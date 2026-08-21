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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

@SuppressWarnings("deprecation")
@Service.Singleton
class MessagingEntryPointsImpl implements MessagingEntryPoint.EntryPoints {
    private final boolean noInterceptors;
    private final List<Interception.EntryPointInterceptor> interceptors;

    @Service.Inject
    MessagingEntryPointsImpl(List<ServiceInstance<Interception.EntryPointInterceptor>> entryPointInterceptors) {
        this.noInterceptors = entryPointInterceptors.isEmpty();
        this.interceptors = interceptors(entryPointInterceptors);
    }

    @Override
    public <T> MessagingEntryPoint.Handler<T> handler(ServiceDescriptor<?> descriptor,
                                                      Set<Qualifier> typeQualifiers,
                                                      List<Annotation> typeAnnotations,
                                                      TypedElementInfo methodInfo,
                                                      MessagingEntryPoint.Handler<T> actualHandler) {
        if (noInterceptors) {
            return actualHandler;
        }

        InterceptionContext baseContext = context(descriptor, typeAnnotations, methodInfo);
        return (serviceInstance, message) -> new Invocation<>(context(baseContext, serviceInstance),
                                                               interceptors,
                                                               serviceInstance,
                                                               actualHandler)
                .proceed(new Object[] {message});
    }

    @Override
    public <T> MessagingEntryPoint.BatchHandler<T> batchHandler(ServiceDescriptor<?> descriptor,
                                                                Set<Qualifier> typeQualifiers,
                                                                List<Annotation> typeAnnotations,
                                                                TypedElementInfo methodInfo,
                                                                MessagingEntryPoint.BatchHandler<T> actualHandler) {
        if (noInterceptors) {
            return actualHandler;
        }

        InterceptionContext baseContext = context(descriptor, typeAnnotations, methodInfo);
        return (serviceInstance, messages) -> new BatchInvocation<>(context(baseContext, serviceInstance),
                                                                     interceptors,
                                                                     serviceInstance,
                                                                     actualHandler)
                .proceed(new Object[] {messages});
    }

    private static InterceptionContext context(ServiceDescriptor<?> descriptor,
                                               List<Annotation> typeAnnotations,
                                               TypedElementInfo methodInfo) {
        return InterceptionContext.builder()
                .typeAnnotations(typeAnnotations)
                .elementInfo(methodInfo)
                .serviceInfo(descriptor)
                .build();
    }

    private static InterceptionContext context(InterceptionContext baseContext, Object serviceInstance) {
        return InterceptionContext.builder(baseContext)
                .serviceInstance(serviceInstance)
                .build();
    }

    private static List<Interception.EntryPointInterceptor> interceptors(
            List<ServiceInstance<Interception.EntryPointInterceptor>> entryPointInterceptors) {
        List<WeightedInterceptor> weighted = new ArrayList<>();
        entryPointInterceptors.stream()
                .map(instance -> new WeightedInterceptor(instance.get(), instance.weight()))
                .forEach(weighted::add);
        Weights.sort(weighted);
        return weighted.stream()
                .map(WeightedInterceptor::interceptor)
                .toList();
    }

    private record WeightedInterceptor(Interception.EntryPointInterceptor interceptor,
                                       double weight) implements Weighted {
    }

    private static final class Invocation<T> implements Interception.Interceptor.Chain<Optional<Message<?>>> {
        private final InterceptionContext context;
        private final List<Interception.EntryPointInterceptor> interceptors;
        private final T serviceInstance;
        private final MessagingEntryPoint.Handler<T> actualHandler;

        private int interceptorPosition;

        private Invocation(InterceptionContext context,
                           List<Interception.EntryPointInterceptor> interceptors,
                           T serviceInstance,
                           MessagingEntryPoint.Handler<T> actualHandler) {
            this.context = context;
            this.interceptors = interceptors;
            this.serviceInstance = serviceInstance;
            this.actualHandler = actualHandler;
        }

        @Override
        public Optional<Message<?>> proceed(Object[] arguments) throws Exception {
            if (interceptorPosition < interceptors.size()) {
                Interception.EntryPointInterceptor interceptor = interceptors.get(interceptorPosition++);
                try {
                    return interceptor.proceed(context, this, arguments);
                } catch (Exception e) {
                    interceptorPosition--;
                    throw e;
                }
            }
            return actualHandler.handle(serviceInstance, message(arguments));
        }

        private Message<?> message(Object[] arguments) {
            if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof Message<?> message)) {
                throw new IllegalArgumentException("Messaging entry point requires exactly one Message argument");
            }
            return message;
        }
    }

    private static final class BatchInvocation<T> implements Interception.Interceptor.Chain<Void> {
        private final InterceptionContext context;
        private final List<Interception.EntryPointInterceptor> interceptors;
        private final T serviceInstance;
        private final MessagingEntryPoint.BatchHandler<T> actualHandler;

        private int interceptorPosition;

        private BatchInvocation(InterceptionContext context,
                                List<Interception.EntryPointInterceptor> interceptors,
                                T serviceInstance,
                                MessagingEntryPoint.BatchHandler<T> actualHandler) {
            this.context = context;
            this.interceptors = interceptors;
            this.serviceInstance = serviceInstance;
            this.actualHandler = actualHandler;
        }

        @Override
        public Void proceed(Object[] arguments) throws Exception {
            if (interceptorPosition < interceptors.size()) {
                Interception.EntryPointInterceptor interceptor = interceptors.get(interceptorPosition++);
                try {
                    return interceptor.proceed(context, this, arguments);
                } catch (Exception e) {
                    interceptorPosition--;
                    throw e;
                }
            }
            actualHandler.handle(serviceInstance, messages(arguments));
            return null;
        }

        private MessageBatch<?> messages(Object[] arguments) {
            if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof MessageBatch<?> messages)) {
                throw new IllegalArgumentException("Messaging batch entry point requires exactly one MessageBatch argument");
            }
            for (Object item : messages) {
                if (!(item instanceof Message<?>)) {
                    throw new IllegalArgumentException("Messaging batch entry point requires Message elements");
                }
            }
            return messages;
        }
    }
}
