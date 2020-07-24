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

package io.helidon.microprofile.metrics;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

/**
 * Interceptor for {@link SimplyTimed} annotation.
 * <p>
 *     Note that this interceptor fires only for explicit {@code SimplyTimed} annotations.
 *     The CDI extension adds synthetic {@code SimplyTimed} annotations to each JAX-RS
 *     method, and the separate {@link InterceptorInferredSimplyTimed} interceptor deals with those.
 * </p>
 */
@SimplyTimed
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
final class InterceptorSimplyTimed extends InterceptorBase<SimpleTimer, SimplyTimed> {

    @Inject
    InterceptorSimplyTimed(MetricRegistry registry) {
        super(registry,
                SimplyTimed.class,
                SimplyTimed::name,
                SimplyTimed::tags,
                SimplyTimed::absolute,
                "simpletimer",
                SimpleTimer.class);
    }

    @Override
    protected Object prepareAndInvoke(SimpleTimer simpleTimer, SimplyTimed annotation, InvocationContext context) throws Exception {
        return simpleTimer.time(context::proceed);
    }
}
