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

package io.helidon.common.types;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class ElementSignatures {
    private ElementSignatures() {
    }

    static ElementSignature createNone() {
        return new NoSignature();
    }

    static ElementSignature createField(TypeName type,
                                        String name) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        return new FieldSignature(type, name);
    }

    static ElementSignature createConstructor(List<TypeName> parameters) {
        Objects.requireNonNull(parameters);
        return new MethodSignature(TypeNames.PRIMITIVE_VOID,
                                   "<init>",
                                   parameters);
    }

    static ElementSignature createMethod(TypeName returnType, String name, List<TypeName> parameters) {
        Objects.requireNonNull(returnType);
        Objects.requireNonNull(name);
        Objects.requireNonNull(parameters);
        return new MethodSignature(returnType,
                                   name,
                                   parameters);
    }

    static ElementSignature createParameter(TypeName type, String name) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        return new ParameterSignature(type, name);
    }

    static final class FieldSignature implements ElementSignature {
        private final TypeName type;
        private final String name;

        private FieldSignature(TypeName type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public TypeName type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<TypeName> parameterTypes() {
            return List.of();
        }

        @Override
        public String text() {
            return name;
        }

        @Override
        public String toString() {
            return type.resolvedName() + " " + name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FieldSignature that)) {
                return false;
            }
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    static final class MethodSignature implements ElementSignature {
        private final TypeName type;
        private final String name;
        private final List<TypeName> parameters;
        private final String text;
        private final boolean constructor;

        private MethodSignature(TypeName type,
                                String name,
                                List<TypeName> parameters) {
            this.type = type;
            this.name = name;
            this.parameters = parameters;
            if (name.equals("<init>")) {
                this.constructor = true;
                this.text = "(" + parameters.stream()
                        .map(TypeName::fqName)
                        .collect(Collectors.joining(","))
                        + ")";
            } else {
                this.constructor = false;
                this.text = name + "(" + parameters.stream()
                        .map(TypeName::fqName)
                        .collect(Collectors.joining(","))
                        + ")";
            }

        }

        @Override
        public TypeName type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<TypeName> parameterTypes() {
            return parameters;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String toString() {
            if (constructor) {
                return text;
            } else {
                return type.resolvedName() + " " + name + "("
                        + parameters.stream()
                        .map(TypeName::resolvedName)
                        .collect(Collectors.joining(", "))
                        + ")";
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MethodSignature that)) {
                return false;
            }
            return Objects.equals(name, that.name) && Objects.equals(parameters, that.parameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, parameters);
        }
    }

    static final class ParameterSignature implements ElementSignature {
        private final TypeName type;
        private final String name;

        private ParameterSignature(TypeName type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public TypeName type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<TypeName> parameterTypes() {
            return List.of();
        }

        @Override
        public String text() {
            return name;
        }

        @Override
        public String toString() {
            return type.resolvedName() + " " + name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ParameterSignature that)) {
                return false;
            }
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    static final class NoSignature implements ElementSignature {
        @Override
        public TypeName type() {
            return TypeNames.PRIMITIVE_VOID;
        }

        @Override
        public String name() {
            return "<INVALID>";
        }

        @Override
        public String text() {
            return "<INVALID>";
        }

        @Override
        public String toString() {
            return text();
        }

        @Override
        public List<TypeName> parameterTypes() {
            return List.of();
        }
    }
}
