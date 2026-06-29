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
package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.microprofile.testing.AddBean;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.glassfish.jersey.process.internal.RequestContext;
import org.glassfish.jersey.process.internal.RequestScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@AddBean(RequestScopeRetentionTest.TestBean.class)
@AddBean(value = RequestScopeRetentionTest.TrackingRequestScope.class, scope = Dependent.class)
class RequestScopeRetentionTest extends FaultToleranceTest {

    @Inject
    private TestBean bean;

    @BeforeEach
    void resetCounters() {
        TrackingRequestScope.CREATED.set(0);
        TrackingRequestScope.DESTROYED.set(0);
    }

    @Test
    void synchronousInvocationDoesNotCreateRequestScope() {
        assertThat(bean.synchronous(), is("done"));
        assertThat(TrackingRequestScope.CREATED.get(), is(0));
        assertThat(TrackingRequestScope.DESTROYED.get(), is(0));
    }

    @Test
    void asynchronousInvocationReleasesRequestScope() throws Exception {
        CompletionStage<String> result = bean.asynchronous();

        assertThat(result.toCompletableFuture().get(5, TimeUnit.SECONDS), is("done"));
        assertThat(TrackingRequestScope.CREATED.get(), is(1));
        assertThat(TrackingRequestScope.DESTROYED.get(), is(1));
    }

    static class TestBean {

        @Timeout(value = 1, unit = ChronoUnit.MINUTES)
        String synchronous() {
            return "done";
        }

        @Asynchronous
        CompletionStage<String> asynchronous() {
            return CompletableFuture.completedFuture("done");
        }
    }

    @Alternative
    @Priority(1)
    static class TrackingRequestScope extends RequestScope {
        private static final AtomicInteger CREATED = new AtomicInteger();
        private static final AtomicInteger DESTROYED = new AtomicInteger();

        TrackingRequestScope() {
            CREATED.incrementAndGet();
        }

        @Override
        public RequestContext createContext() {
            throw new UnsupportedOperationException();
        }

        @PreDestroy
        void destroy() {
            DESTROYED.incrementAndGet();
        }
    }
}
