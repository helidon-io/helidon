/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Class FallbackBean.
 */
@Dependent
public class FallbackBean {

    private AtomicBoolean called = new AtomicBoolean(false);

    public boolean getCalled() {
        return called.get();
    }

    @Fallback(fallbackMethod = "onFailure")
    public String fallback() {
        called.set(true);
        FaultToleranceTest.printStatus("FallbackBean::fallback()", "failure");
        throw new RuntimeException("Oops");
    }

    public String onFailure() {
        FaultToleranceTest.printStatus("FallbackBean::onFailure()", "success");
        return "fallback";
    }

    @Fallback(FallbackBeanHandler.class)
    public String fallbackHandler(String value) {
        called.set(true);
        FaultToleranceTest.printStatus("FallbackBean::fallbackHandler()", "failure");
        throw new RuntimeException("Oops");
    }

    static class FallbackBeanHandler implements FallbackHandler<String> {

        @Override
        public String handle(ExecutionContext context) {
            FaultToleranceTest.printStatus("FallbackBeanHandler::handle()", "success");
            assertThat(context, is(notNullValue()));
            assertThat(context.getMethod().getName(), is("fallbackHandler"));
            assertThat(context.getParameters().length, is(1));
            assertThat(context.getParameters()[0], is("someValue"));
            return "fallbackHandler";
        }
    }
}
