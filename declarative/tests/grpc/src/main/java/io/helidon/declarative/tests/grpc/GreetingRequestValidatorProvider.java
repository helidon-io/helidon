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

package io.helidon.declarative.tests.grpc;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.service.registry.Service;
import io.helidon.validation.ValidatorContext;
import io.helidon.validation.ValidatorResponse;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.Singleton
@Service.NamedByType(ValidGreetingRequest.class)
class GreetingRequestValidatorProvider implements ConstraintValidatorProvider {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Override
    public ConstraintValidator create(TypeName typeName, Annotation constraintAnnotation) {
        return new GreetingRequestValidator(constraintAnnotation);
    }

    static void reset() {
        INVOCATIONS.set(0);
    }

    static int invocations() {
        return INVOCATIONS.get();
    }

    private static class GreetingRequestValidator implements ConstraintValidator {
        private final Annotation annotation;

        private GreetingRequestValidator(Annotation annotation) {
            this.annotation = annotation;
        }

        @Override
        public ValidatorResponse check(ValidatorContext context, Object value) {
            INVOCATIONS.incrementAndGet();
            if (value instanceof GreetingRequest request && !request.getName().isBlank()) {
                return ValidatorResponse.create();
            }
            return ValidatorResponse.create(annotation, "name is blank", value);
        }
    }
}
