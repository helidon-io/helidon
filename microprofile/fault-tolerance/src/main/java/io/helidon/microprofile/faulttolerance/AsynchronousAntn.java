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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

class AsynchronousAntn extends MethodAntn implements Asynchronous {

    /**
     * Constructor.
     *
     * @param annotatedMethod The annotated method.
     */
    AsynchronousAntn(AnnotatedMethod<?> annotatedMethod) {
        super(annotatedMethod);
    }

    @Override
    public void validate() {
        Class<?> returnType = method().getReturnType();
        if (!Future.class.isAssignableFrom(returnType) && !CompletionStage.class.isAssignableFrom(returnType)) {
            throw new FaultToleranceDefinitionException("Asynchronous method '" + method().getName()
                    + "' must return Future or CompletionStage");
        }
    }
}
