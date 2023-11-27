/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;

import static io.helidon.common.types.TypeNames.PRIMITIVES;

final class TypeNameSupport {
    private TypeNameSupport() {
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static int compareTo(TypeName typeName, TypeName o) {
        int diff = typeName.name().compareTo(o.name());
        if (diff != 0) {
            // different name
            return diff;
        }
        diff = Boolean.compare(typeName.primitive(), o.primitive());
        if (diff != 0) {
            return diff;
        }
        return Boolean.compare(typeName.array(), o.array());
    }

    /**
     * Return the boxed equivalent of this type.
     * If this is not a primitive type, returns this instance.
     *
     * @param original instance to box
     * @return boxed type for this type, or this type if not primitive
     */
    @Prototype.PrototypeMethod
    static TypeName boxed(TypeName original) {
        return TypeNames.boxed(original);
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static String toString(TypeName instance) {
        return instance.resolvedName();
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override") // defined on blueprint
    static String name(TypeName instance) {
        return calcName(instance, "$");
    }

    /**
     * The base generic type name, stripped of any {@link TypeName#typeArguments()}.
     * This is equivalent to the type name represented by {@link TypeName#name()}.
     *
     * @param instance the instance
     * @return based generic type name
     */
    @Prototype.PrototypeMethod
    static TypeName genericTypeName(TypeName instance) {
        return TypeName.builder(instance).typeArguments(List.of()).generic(false).array(false).build();
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override") // defined on blueprint
    static String fqName(TypeName instance) {
        String name = calcName(instance, ".");
        StringBuilder nameBuilder = new StringBuilder(instance.wildcard() ? "?" : name);
        if (instance.array()) {
            nameBuilder.append("[]");
        }
        return nameBuilder.toString();
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override") // defined on blueprint
    static String resolvedName(TypeName instance) {
        String name = calcName(instance, ".");
        boolean isObject = Object.class.getName().equals(name) || "?".equals(name);
        StringBuilder nameBuilder = (isObject)
                ? new StringBuilder(instance.wildcard() ? "?" : name)
                : new StringBuilder(instance.wildcard() ? "? extends " + name : name);

        if (!instance.typeArguments().isEmpty()) {
            nameBuilder.append("<");
            int i = 0;
            for (TypeName param : instance.typeArguments()) {
                if (i > 0) {
                    nameBuilder.append(", ");
                }
                nameBuilder.append(param.resolvedName());
                i++;
            }
            nameBuilder.append(">");
        }

        if (instance.array()) {
            nameBuilder.append("[]");
        }

        return nameBuilder.toString();
    }

    /**
     * Update builder from the provided type.
     *
     * @param builder builder to update
     * @param type type to get information (package name, class name, primitive, array)
     */
    @Prototype.BuilderMethod
    static void type(TypeName.BuilderBase<?, ?> builder, Type type) {
        Objects.requireNonNull(type);
        if (type instanceof Class<?> classType) {
            Class<?> componentType = classType.isArray() ? classType.getComponentType() : classType;
            builder.packageName(componentType.getPackageName());
            builder.className(componentType.getSimpleName());
            builder.primitive(componentType.isPrimitive());
            builder.array(classType.isArray());

            Class<?> enclosingClass = classType.getEnclosingClass();
            LinkedList<String> enclosingTypes = new LinkedList<>();
            while (enclosingClass != null) {
                enclosingTypes.addFirst(enclosingClass.getSimpleName());
                enclosingClass = enclosingClass.getEnclosingClass();
            }
            builder.enclosingNames(enclosingTypes);
        } else {
            // todo
            throw new IllegalArgumentException("Currently we only support class as a parameter, but got: " + type);
        }
    }

    /**
     * Create a type name from a type (such as class).
     *
     * @param type the type
     * @return type name for the provided type
     */
    @Prototype.FactoryMethod
    static TypeName create(Type type) {
        return TypeName.builder()
                .type(type)
                .build();
    }

    /**
     * Creates a type name from a fully qualified class name.
     *
     * @param typeName the FQN of the class type
     * @return the TypeName for the provided type name
     */
    @Prototype.FactoryMethod
    static TypeName create(String typeName) {
        Objects.requireNonNull(typeName);
        if (typeName.startsWith("?")) {
            if (typeName.startsWith("? extends ")) {
                return TypeName.builder(create(typeName.substring(10).trim()))
                        .wildcard(true)
                        .build();
            } else {
                return TypeName.builder()
                        .type(Object.class)
                        .wildcard(true)
                        .build();
            }
        }

        // handle primitives from their names
        TypeName primitive = PRIMITIVES.get(typeName);
        if (primitive != null) {
            return primitive;
        }

        TypeName.Builder builder = TypeName.builder();
        // we are expecting something like `java.lang.Consumer<java.lang.String>`
        int index = typeName.indexOf('<');
        if (index > 0) {
            String genericSection = typeName.substring(index + 1, typeName.length() - 1);
            typeName = typeName.substring(0, index);

            // A, java.lang.String, B
            Stream.of(genericSection.split(","))
                    .map(String::trim) // remove possible spaces
                    .map(it -> {
                        if (it.contains(".")) {
                            return TypeName.create(it);
                        } else {
                            return TypeName.createFromGenericDeclaration(it);
                        }
                    })
                    .forEach(builder::addTypeArgument);
        }
        // a.b.c.SomeClass
        // a.b.c.SomeClass.InnerClass.Builder
        String className = typeName;
        List<String> packageElements = new ArrayList<>();

        while (true) {
            if (className.isEmpty()) {
                throw new IllegalArgumentException("Invalid type name: \"" + typeName + "\", got empty string section");
            }
            if (Character.isUpperCase(className.charAt(0))) {
                break;
            }
            int dot = className.indexOf('.');
            if (dot == -1) {
                // no more dots, we have the class name
                break;
            }
            packageElements.add(className.substring(0, dot));
            className = className.substring(dot + 1);
        }

        if (packageElements.isEmpty()) {
            return builder.className(typeName)
                    .build();
        }

        String packageName = String.join(".", packageElements);
        String[] types = className.split("\\.");
        return builder.packageName(packageName)
                .update(it -> {
                    for (int i = 0; i < (types.length - 1); i++) {
                        it.addEnclosingName(types[i]);
                    }
                })
                .className(types[types.length - 1])
                .build();
    }

    /**
     * Creates a type name from a generic alias type name.
     *
     * @param genericAliasTypeName the generic alias type name
     * @return the TypeName for the provided type name
     */
    @Prototype.FactoryMethod
    static TypeName createFromGenericDeclaration(String genericAliasTypeName) {
        return TypeName.builder()
                .generic(true)
                .className(Objects.requireNonNull(genericAliasTypeName))
                .wildcard(genericAliasTypeName.startsWith("?"))
                .build();
    }

    private static String calcName(TypeName instance, String typeSeparator) {
        String className;
        if (instance.enclosingNames().isEmpty()) {
            className = instance.className();
        } else {
            className = String.join(typeSeparator, instance.enclosingNames()) + typeSeparator + instance.className();
        }

        return (instance.primitive() || instance.packageName().isEmpty())
                ? className : instance.packageName() + "." + className;
    }
}
