/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Set;

class MethodPredicates {
    static class TruePredicate implements MethodPredicate {
        private static final TruePredicate INSTANCE = new TruePredicate();

        static MethodPredicate get() {
            return INSTANCE;
        }

        @Override
        public boolean test(Method t) {
            return true;
        }

        @Override
        public Set<Method> acceptedMethods() {
            return Set.of();
        }

        @Override
        public String toString() {
            return "(any method)";
        }
    }

    static class SingleMethodEnumPredicate implements MethodPredicate {
        private final Method method;
        private final Set<Method> accepted;

        SingleMethodEnumPredicate(Method method) {
            this.method = method;
            this.accepted = Set.of(method);
        }

        @Override
        public boolean test(Method method) {
            return method == this.method;
        }

        @Override
        public Set<Method> acceptedMethods() {
            return accepted;
        }

        @Override
        public String toString() {
            return method.text();
        }
    }

    static class SingleMethodPredicate implements MethodPredicate {
        private final Method method;
        private final Set<Method> accepted;

        SingleMethodPredicate(Method method) {
            this.method = method;
            this.accepted = Set.of(method);
        }

        @Override
        public boolean test(Method method) {
            return method.equals(this.method);
        }

        @Override
        public Set<Method> acceptedMethods() {
            return accepted;
        }

        @Override
        public String toString() {
            return method.text();
        }
    }

    static class MethodsPredicate implements MethodPredicate {
        private final Set<Method> methods;

        MethodsPredicate(Method... methods) {
            this.methods = Set.of(methods);
        }

        @Override
        public boolean test(Method method) {
            return methods.contains(method);
        }

        @Override
        public Set<Method> acceptedMethods() {
            return methods;
        }

        @Override
        public String toString() {
            return String.join(", ", methods.stream().map(Method::text).toList());
        }
    }
}
