/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.annotation.Priority;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.microprofile.metrics.ConcurrentGauge;

/**
 * Interceptor for {@link ConcurrentGauge} annotation.
 */
@InterceptorConcurrentGauge.Binding
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 11)
final class InterceptorConcurrentGauge extends MetricsInterceptorBase.WithPostCompletion<ConcurrentGauge> {
    InterceptorConcurrentGauge() {
        super(org.eclipse.microprofile.metrics.annotation.ConcurrentGauge.class, ConcurrentGauge.class);
    }

    static Binding.Literal binding() {
        return Binding.Literal.instance();
    }

    @Override
    void preInvoke(ConcurrentGauge metric) {
        metric.inc();
    }

    @Override
    void postComplete(ConcurrentGauge metric) {
        metric.dec();
    }
    @Inherited
    @InterceptorBinding
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @org.eclipse.microprofile.metrics.annotation.ConcurrentGauge
    @interface Binding {
        class Literal extends AnnotationLiteral<Binding> implements Binding {

            private static final long serialVersionUID = 1L;

            private static final Literal INSTANCE = new Literal();

            static Literal instance() {
                return INSTANCE;
            }

            private Literal() {
            }
        }
    }
}
