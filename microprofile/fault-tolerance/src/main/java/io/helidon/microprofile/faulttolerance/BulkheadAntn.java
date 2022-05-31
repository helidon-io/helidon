/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

class BulkheadAntn extends MethodAntn implements Bulkhead {

    /**
     * Constructor.
     *
     * @param annotatedMethod The annotated method.
     */
    BulkheadAntn(AnnotatedMethod<?> annotatedMethod) {
        super(annotatedMethod);
    }

    @Override
    public void validate() {
        if (value() <= 0) {
            throw new FaultToleranceDefinitionException("Invalid @Bulkhead annotation, "
                                                        + "value must be > 0");
        }
        if (waitingTaskQueue() <= 0) {
            throw new FaultToleranceDefinitionException("Invalid @Bulkhead annotation, "
                                                        + "waitingTaskQueue must be > 0");
        }
    }

    @Override
    public int value() {
        LookupResult<Bulkhead> lookupResult = lookupAnnotation(Bulkhead.class);
        final String override = getParamOverride("value", lookupResult.getType());
        return override != null ? Integer.parseInt(override) : lookupResult.getAnnotation().value();
    }

    @Override
    public int waitingTaskQueue() {
        LookupResult<Bulkhead> lookupResult = lookupAnnotation(Bulkhead.class);
        final String override = getParamOverride("waitingTaskQueue", lookupResult.getType());
        return override != null ? Integer.parseInt(override) : lookupResult.getAnnotation().waitingTaskQueue();
    }
}
