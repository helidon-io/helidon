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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

import io.helidon.validation.spi.ConstraintValidator;

class ValidationContextImpl implements ValidationContext {
    private final Stack<ConstraintViolation.PathElement> pathStack = new Stack<>();
    private final Object rootObject;
    private final Class<?> rootType;
    private final ValidatorContext validatorContext;
    private final ValidationContextConfig config;

    private ValidatorResponseHidden response = OkResponse.INSTANCE;

    ValidationContextImpl(ValidationContextConfig config) {
        this.config = config;
        this.rootType = config.rootType();
        this.rootObject = config.rootObject().orElse(null);

        this.validatorContext = new ValidatorContextImpl(config.clock());
    }

    @Override
    public ValidationContextConfig prototype() {
        return config;
    }

    @Override
    public ValidationResponse response() {
        var toReturn = response;
        this.response = OkResponse.INSTANCE;
        return toReturn;
    }

    @Override
    public Scope scope(ConstraintViolation.Location location, String name) {
        this.pathStack.push(new PathImpl(location, name));
        return this.pathStack::pop;
    }

    @Override
    public void check(ConstraintValidator validator, Object object) {
        var checkerResponse = validator.check(validatorContext, object);
        if (checkerResponse.valid()) {
            return;
        }

        ConstraintViolation violation = new ConstraintViolationImpl(rootType,
                                                                    Optional.ofNullable(rootObject),
                                                                    new ArrayList<>(pathStack),
                                                                    checkerResponse.invalidValue(),
                                                                    checkerResponse.message(),
                                                                    checkerResponse.annotation());

        this.response = this.response.merge(new FailedResponse(violation));
    }

    private interface ValidatorResponseHidden extends ValidationResponse {
        ValidatorResponseHidden merge(ValidatorResponseHidden other);
    }

    record PathImpl(ConstraintViolation.Location location, String name) implements ConstraintViolation.PathElement {
        @Override
        public String toString() {
            return location + "(" + name + ")";
        }
    }

    private static class FailedResponse implements ValidatorResponseHidden {

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
        public boolean valid() {
            return false;
        }

        @Override
        public String message() {
            return violations.stream()
                    .map(ConstraintViolation::message)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public ValidatorResponseHidden merge(ValidatorResponseHidden other) {
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

    private static class OkResponse implements ValidatorResponseHidden {
        private static final OkResponse INSTANCE = new OkResponse();

        private OkResponse() {
        }

        @Override
        public boolean valid() {
            return true;
        }

        @Override
        public String message() {
            return "";
        }

        @Override
        public ValidatorResponseHidden merge(ValidatorResponseHidden other) {
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
