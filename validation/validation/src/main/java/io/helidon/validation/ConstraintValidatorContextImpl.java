/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.validation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

import io.helidon.common.types.Annotation;

class ConstraintValidatorContextImpl implements ValidationContext {
    private final Object rootObject;
    private final Class<?> rootType;
    private final Clock clock;
    private final Stack<ConstraintViolation.PathElement> pathStack = new Stack<>();

    ConstraintValidatorContextImpl(Class<?> rootType, Object rootObject) {
        this(rootType, rootObject, Clock.systemUTC());
    }

    ConstraintValidatorContextImpl(Class<?> rootType, Object rootObject, Clock clock) {
        this.rootType = rootType;
        this.rootObject = rootObject;
        this.clock = clock;
    }

    @Override
    public ValidatorResponse response(Annotation annotation, String message, Object invalidValue) {
        ConstraintViolation violation = new ConstraintViolationImpl(rootType,
                                                                    Optional.ofNullable(rootObject),
                                                                    new ArrayList<>(pathStack),
                                                                    invalidValue,
                                                                    message,
                                                                    annotation);
        return new FailedResponse(violation);

    }

    @Override
    public ValidatorResponse response() {
        return OkResponse.INSTANCE;
    }

    @Override
    public void enter(ConstraintViolation.Location location, String name) {
        this.pathStack.push(new PathImpl(location, name));
    }

    @Override
    public void leave() {
        this.pathStack.pop();
    }

    @Override
    public Clock clock() {
        return clock;
    }

    record PathImpl(ConstraintViolation.Location location, String name) implements ConstraintViolation.PathElement {
        @Override
        public String toString() {
            return location + "(" + name + ")";
        }
    }

    private static class FailedResponse implements ValidatorResponse {

        private final List<ConstraintViolation> violations;

        private FailedResponse(ConstraintViolation violation) {
            this.violations = List.of(violation);
        }

        private FailedResponse(List<ConstraintViolation> violations) {
            this.violations = List.copyOf(violations);
        }

        @Override
        public List<ConstraintViolation> violations() {
            return violations;
        }

        @Override
        public boolean failed() {
            return true;
        }

        @Override
        public String message() {
            return violations.stream()
                    .map(ConstraintViolation::message)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public ValidatorResponse merge(ValidatorResponse other) {
            List<ConstraintViolation> violations = new ArrayList<>(this.violations);
            violations.addAll(other.violations());
            return new FailedResponse(violations);
        }

        @Override
        public ValidationException toException() {
            throw new ValidationException("Constraint validation failed: " + exceptionMessage(), violations());
        }

        private String exceptionMessage() {
            return violations.stream()
                    .map(it -> it.message() + " at " + pathToString(it.location()))
                    .collect(Collectors.joining(", "));
        }

        private String pathToString(List<ConstraintViolation.PathElement> location) {
            return location.stream()
                    .map(ConstraintViolation.PathElement::toString)
                    .collect(Collectors.joining("/"));
        }
    }

    private static class OkResponse implements ValidatorResponse {
        private static final OkResponse INSTANCE = new OkResponse();

        private OkResponse() {
        }

        @Override
        public boolean failed() {
            return false;
        }

        @Override
        public String message() {
            return "";
        }

        @Override
        public ValidatorResponse merge(ValidatorResponse other) {
            return other;
        }

        @Override
        public ValidationException toException() {
            throw new IllegalStateException("Cannot create an exception for a response that did not fail");
        }

        @Override
        public List<ConstraintViolation> violations() {
            return List.of();
        }
    }
}
