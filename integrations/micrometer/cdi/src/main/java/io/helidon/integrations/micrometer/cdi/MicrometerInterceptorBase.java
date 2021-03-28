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
package io.helidon.integrations.micrometer.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;

import io.helidon.integrations.micrometer.cdi.MicrometerCdiExtension.MeterWorkItem;
import io.helidon.servicecommon.restcdi.HelidonInterceptor;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Base implementation for all Micrometer interceptors.
 * <p>
 *     Both interceptors use the with-post-completion" semantics; {@code Timed} needs to update the metric only after the
 *     method has completed, and {@code Counted} might use update-only-on-failure, which of course it does not know until
 *     completion.
 * </p>
 * @param <M>
 */
@Dependent
abstract class MicrometerInterceptorBase<M extends Meter> extends HelidonInterceptor.Base<MeterWorkItem>
        implements HelidonInterceptor.WithPostCompletion<MeterWorkItem> {

    private static final Logger LOGGER = Logger.getLogger(MicrometerInterceptorBase.class.getPackageName() + ".Interceptor*");

    private final Class<? extends Annotation> annotationType;
    private final Class<M> meterType;

    @Inject
    private MicrometerCdiExtension extension;

    @Inject
    private MeterRegistry registry;

    private final Map<AnnotatedElement, M> elementMeterMap = new ConcurrentHashMap<>();

    enum ActionType {
        PREINVOKE("preinvoke"), COMPLETE("complete");

        private final String label;

        ActionType(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }

    @FunctionalInterface
    interface MeterLookup<M extends Meter> {
         M apply(MeterRegistry meterRegistry, String name, String[] tags);
    }

    MicrometerInterceptorBase(Class<? extends Annotation> annotationType, Class<M> meterType) {
        this.annotationType = annotationType;
        this.meterType = meterType;
    }

    @Override
    public Iterable<MeterWorkItem> workItems(Executable executable) {
        return extension.workItems(executable, annotationType);
    }

    @Override
    public void preInvocation(InvocationContext context, MeterWorkItem workItem) {
        verifyAction(context, workItem, this::preInvoke, ActionType.PREINVOKE);
    }

    @Override
    public void postCompletion(InvocationContext context, Throwable throwable, MeterWorkItem workItem) {
        if (!workItem.isOnlyOnException() || throwable != null) {
            verifyAction(context, workItem, this::postComplete, ActionType.COMPLETE);
        }
    }

    private void verifyAction(InvocationContext context, MeterWorkItem workItem, Consumer<M> action, ActionType actionType) {
        Meter meter = workItem.meter();
        if (registry
                .find(meter.getId().getName())
                .tags(meter.getId().getTags())
                .meter() == null) {
            throw new IllegalStateException("Attempt to use previously-removed metric" + workItem.meter().getId());
        }
        LOGGER.log(Level.FINEST, () -> String.format(
                "%s (%s) is accepting %s %s for processing on %s triggered by @%s",
                getClass().getSimpleName(), actionType, workItem.meter().getClass().getSimpleName(), workItem.meter().getId(),
                context.getMethod() != null ? context.getMethod() : context.getConstructor(), annotationType.getSimpleName()));
        action.accept(meterType.cast(meter));
    }

    void preInvoke(M meter) {
    }

    abstract void postComplete(M meter);

}
