/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import jakarta.enterprise.inject.spi.BeanManager;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidateWithExecutorTest {

    static class BadWithExecutorBean {

        @Asynchronous
        @WithExecutor("does-not-exist")
        public CompletableFuture<?> shouldNotBeCalled() {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void badExecutorTest() throws Exception {
        Method method = BadWithExecutorBean.class.getMethod("shouldNotBeCalled");
        WithExecutor withExecutor = method.getAnnotation(WithExecutor.class);
        BeanManager bm = Mockito.mock(BeanManager.class);
        Mockito.when(bm.getBeans(ExecutorService.class, withExecutor)).thenReturn(Collections.emptySet());
        assertThrows(FaultToleranceDefinitionException.class,
                     () -> FaultToleranceExtension.validateWithExecutor(bm, method));
    }
}
