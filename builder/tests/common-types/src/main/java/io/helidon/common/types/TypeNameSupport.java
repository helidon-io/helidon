/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;

final class TypeNameSupport {
    private static final TypeName PRIMITIVE_BOOLEAN = TypeName.create(boolean.class);
    private static final TypeName PRIMITIVE_BYTE = TypeName.create(byte.class);
    private static final TypeName PRIMITIVE_SHORT = TypeName.create(short.class);
    private static final TypeName PRIMITIVE_INT = TypeName.create(int.class);
    private static final TypeName PRIMITIVE_LONG = TypeName.create(long.class);
    private static final TypeName PRIMITIVE_CHAR = TypeName.create(char.class);
    private static final TypeName PRIMITIVE_FLOAT = TypeName.create(float.class);
    private static final TypeName PRIMITIVE_DOUBLE = TypeName.create(double.class);
    private static final TypeName PRIMITIVE_VOID = TypeName.create(void.class);
    private static final TypeName BOXED_BOOLEAN = TypeName.create(Boolean.class);
    private static final TypeName BOXED_BYTE = TypeName.create(Byte.class);
    private static final TypeName BOXED_SHORT = TypeName.create(Short.class);
    private static final TypeName BOXED_INT = TypeName.create(Integer.class);
    private static final TypeName BOXED_LONG = TypeName.create(Long.class);
    private static final TypeName BOXED_CHAR = TypeName.create(Character.class);
    private static final TypeName BOXED_FLOAT = TypeName.create(Float.class);
    private static final TypeName BOXED_DOUBLE = TypeName.create(Double.class);
    private static final TypeName BOXED_VOID = TypeName.create(Void.class);

    // as type names need this class to be initialized, let's have a copy of these
    private static final Map<String, TypeName> PRIMITIVES = Map.of(
            "boolean", PRIMITIVE_BOOLEAN,
            "byte", PRIMITIVE_BYTE,
            "short", PRIMITIVE_SHORT,
            "int", PRIMITIVE_INT,
            "long", PRIMITIVE_LONG,
            "char", PRIMITIVE_CHAR,
            "float", PRIMITIVE_FLOAT,
            "double", PRIMITIVE_DOUBLE,
            "void", PRIMITIVE_VOID
    );

    private static final Map<TypeName, TypeName> BOXED_TYPES = Map.of(
            PRIMITIVE_BOOLEAN, BOXED_BOOLEAN,
            PRIMITIVE_BYTE, BOXED_BYTE,
            PRIMITIVE_SHORT, BOXED_SHORT,
            PRIMITIVE_INT, BOXED_INT,
            PRIMITIVE_LONG, BOXED_LONG,
            PRIMITIVE_CHAR, BOXED_CHAR,
            PRIMITIVE_FLOAT, BOXED_FLOAT,
            PRIMITIVE_DOUBLE, BOXED_DOUBLE,
            PRIMITIVE_VOID, BOXED_VOID
    );

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
        return Optional.ofNullable(BOXED_TYPES.get(original))
                .orElse(original);
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
        if (instance.generic() || instance.wildcard()) {
            return resolveGenericName(instance);
        }
        return resolveClassName(instance);
    }

    /**
     * Update builder from the provided type.
     *
     * @param builder builder to update
     * @param type    type to get information (package name, class name, primitive, array)
     */
    @Prototype.BuilderMethod
    static void type(TypeName.BuilderBase<?, ?> builder, Type type) {
        Objects.requireNonNull(type);
        if (type instanceof Class<?> classType) {
            updateFromClass(builder, classType);
            return;
        }
        Type reflectGenericType = type;

        if (type instanceof GenericType<?> gt) {
            if (gt.isClass()) {
                // simple case - just a class
                updateFromClass(builder, gt.rawType());
                return;
            } else {
                // complex case - has generic type arguments
                reflectGenericType = gt.type();
            }
        }

        // translate the generic type into type name
        if (reflectGenericType instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> theClass) {
                updateFromClass(builder, theClass);
            } else {
                throw new IllegalArgumentException("Raw type of a ParameterizedType is not a class: " + raw.getClass().getName()
                                                           + ", for " + pt.getTypeName());
            }

            Type[] actualTypeArguments = pt.getActualTypeArguments();
            for (Type actualTypeArgument : actualTypeArguments) {
                builder.addTypeArgument(TypeName.create(actualTypeArgument));
            }
            return;
        }
        if (reflectGenericType instanceof WildcardType) {
            builder.className("?");
            builder.wildcard(true);
            return;
        }
        if (reflectGenericType instanceof TypeVariable<?> tv) {
            for (Type bound : tv.getBounds()) {
                builder.addUpperBound(TypeName.create(bound));
            }

            builder.className(tv.getName())
                    .generic(true);
            return;
        }
        if (reflectGenericType instanceof GenericArrayType ga) {
            TypeName componentType = TypeName.create(ga.getGenericComponentType());

            builder.from(componentType)
                    .array(true);
            return;
        }

        throw new IllegalArgumentException("We can only create a type from a class, GenericType, or a ParameterizedType,"
                                                   + " but got: " + reflectGenericType.getClass().getName());
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
                        if (it.contains(" extends ")) {
                            // T extends io.helidon.webserver.spi.ServerFeature
                            return createExtends(it);
                        } else if (it.contains(" super ")) {
                            // T super java.lang.String
                            return createSuper(it);
                        } else {
                            if (it.contains(".")) {
                                return TypeName.create(it);
                            } else {
                                return TypeName.createFromGenericDeclaration(it);
                            }
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

    private static TypeName createExtends(String typeNames) {
        // T extends io.helidon.webserver.spi.ServerFeature
        int index = typeNames.indexOf(" extends ");

        var builder = TypeName.builder()
                .generic(true)
                .className(typeNames.substring(0, index));
        String theOtherPart = typeNames.substring(index + 9);
        if (theOtherPart.contains(".")) {
            builder.addUpperBound(TypeName.create(theOtherPart));
        } else {
            builder.addUpperBound(TypeName.createFromGenericDeclaration(theOtherPart));
        }
        return builder.build();
    }

    private static TypeName createSuper(String typeNames) {
        // T extends io.helidon.webserver.spi.ServerFeature
        int index = typeNames.indexOf(" super ");

        var builder = TypeName.builder()
                .generic(true)
                .className(typeNames.substring(0, index));
        String theOtherPart = typeNames.substring(index + 9);
        if (theOtherPart.contains(".")) {
            builder.addLowerBound(TypeName.create(theOtherPart));
        } else {
            builder.addLowerBound(TypeName.createFromGenericDeclaration(theOtherPart));
        }
        return builder.build();
    }

    private static String resolveGenericName(TypeName instance) {
        // ?, ? super Something; ? extends Something
        String prefix = instance.wildcard() ? "?" : instance.className();
        if (instance.upperBounds().isEmpty() && instance.lowerBounds().isEmpty()) {
            return prefix;
        }
        if (instance.lowerBounds().isEmpty()) {
            return prefix + " extends " + instance.upperBounds()
                    .stream()
                    .map(it -> {
                        if (it.generic()) {
                            return it.wildcard() ? "?" : it.className();
                        }
                        return it.resolvedName();
                    })
                    .collect(Collectors.joining(" & "));
        }
        TypeName lowerBound = instance.lowerBounds().getFirst();
        if (lowerBound.generic()) {
            return prefix + " super " + (lowerBound.wildcard() ? "?" : lowerBound.className());
        }
        return prefix + " super " + lowerBound.resolvedName();

    }

    private static String resolveClassName(TypeName instance) {
        String name = calcName(instance, ".");
        StringBuilder nameBuilder = new StringBuilder(name);

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

    private static void updateFromClass(TypeName.BuilderBase<?, ?> builder, Class<?> classType) {
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

    static class Decorator implements Prototype.BuilderDecorator<TypeName.BuilderBase<?, ?>> {
        @Override
        public void decorate(TypeName.BuilderBase<?, ?> target) {
            fixWildcards(target);
        }

        private void fixWildcards(TypeName.BuilderBase<?, ?> target) {
            // handle wildcards correct
            if (target.wildcard()) {
                if (target.upperBounds().size() == 1 && target.lowerBounds().isEmpty()) {
                    // backward compatible for (? extends X)
                    TypeName upperBound = target.upperBounds().getFirst();
                    target.className(upperBound.className());
                    target.packageName(upperBound.packageName());
                    target.enclosingNames(upperBound.enclosingNames());
                }
                // wildcard set, if package + class name as well, set them as upper bounds
                if (target.className().isPresent()
                        && !target.className().get().equals("?")
                        && target.upperBounds().isEmpty()
                        && target.lowerBounds().isEmpty()) {
                    TypeName upperBound = TypeName.builder()
                            .from(target)
                            .wildcard(false)
                            .build();
                    if (!upperBound.equals(TypeNames.OBJECT)) {
                        target.addUpperBound(upperBound);
                    }
                }
                target.generic(true);
            }
        }
    }
}
