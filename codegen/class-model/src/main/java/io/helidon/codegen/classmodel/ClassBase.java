/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

/**
 * Abstract class type model. Contains common logic for all class related models.
 */
public abstract class ClassBase extends AnnotatedComponent {

    private final boolean isFinal;
    private final boolean isAbstract;
    private final boolean isStatic;
    private final List<Field> fields;
    private final List<Field> staticFields;
    private final List<Method> methods;
    private final List<Method> staticMethods;
    private final Set<Type> interfaces;
    private final Set<String> tokenNames;
    private final List<Constructor> constructors;
    private final List<TypeArgument> genericParameters;
    private final List<InnerClass> innerClasses;
    private final ClassType classType;
    private final Type superType;

    ClassBase(Builder<?, ?> builder) {
        super(builder);
        this.isFinal = builder.isFinal;
        this.isAbstract = builder.isAbstract;
        this.isStatic = builder.isStatic;
        if (builder.sortFields) {
            this.fields = builder.fields.values().stream().sorted(ClassBase::fieldComparator).toList();
        } else {
            this.fields = List.copyOf(builder.fields.values());
        }
        if (builder.sortStaticFields) {
            this.staticFields = builder.staticFields.values().stream().sorted(ClassBase::fieldComparator).toList();
        } else {
            this.staticFields = List.copyOf(builder.staticFields.values());
        }
        this.methods = builder.methods.stream().sorted(ClassBase::methodCompare).toList();
        this.staticMethods = builder.staticMethods.stream().sorted(ClassBase::methodCompare).toList();
        this.constructors = List.copyOf(builder.constructors);
        this.interfaces = Collections.unmodifiableSet(new LinkedHashSet<>(builder.interfaces));
        this.innerClasses = List.copyOf(builder.innerClasses.values());
        this.genericParameters = List.copyOf(builder.genericParameters);
        this.tokenNames = this.genericParameters.stream()
                .map(TypeArgument::token)
                .collect(Collectors.toSet());
        this.classType = builder.classType;
        this.superType = builder.superType;
    }

    private static int methodCompare(Method method1, Method method2) {
        if (method1.accessModifier() == method2.accessModifier()) {
            return 0;
        } else {
            return method1.accessModifier().compareTo(method2.accessModifier());
        }
    }

    private static int fieldComparator(Field field1, Field field2) {
        //This is here for ordering purposes.
        if (field1.accessModifier() == field2.accessModifier()) {
            if (field1.isFinal() == field2.isFinal()) {
                if (field1.type().simpleTypeName().equals(field2.type().simpleTypeName())) {
                    if (field1.type().resolvedTypeName().equals(field2.type().resolvedTypeName())) {
                        return field1.name().compareTo(field2.name());
                    }
                    return field1.type().resolvedTypeName().compareTo(field2.type().resolvedTypeName());
                } else if (field1.type().simpleTypeName().equalsIgnoreCase(field2.type().simpleTypeName())) {
                    //To ensure that types with the types with the same name,
                    //but with the different capital letters, will not be mixed
                    return field1.type().simpleTypeName().compareTo(field2.type().simpleTypeName());
                }
                //ignoring case sensitivity to ensure primitive types are properly sorted
                return field1.type().simpleTypeName().compareToIgnoreCase(field2.type().simpleTypeName());
            }
            //final fields should be before non-final
            return Boolean.compare(field2.isFinal(), field1.isFinal());
        } else {
            return field1.accessModifier().compareTo(field2.accessModifier());
        }
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType) throws
            IOException {
        Set<String> combinedTokens = Stream.concat(declaredTokens.stream(), this.tokenNames.stream()).collect(Collectors.toSet());
        if (javadoc().generate()) {
            javadoc().writeComponent(writer, combinedTokens, imports, this.classType);
            writer.write("\n");
        }
        if (!annotations().isEmpty()) {
            for (Annotation annotation : annotations()) {
                annotation.writeComponent(writer, combinedTokens, imports, this.classType);
                writer.write("\n");
            }
        }
        if (AccessModifier.PACKAGE_PRIVATE != accessModifier()) {
            writer.write(accessModifier().modifierName() + " ");
        }
        if (isStatic) {
            writer.write("static ");
        }
        if (isFinal) {
            writer.write("final ");
        }
        if (isAbstract) {
            if (isFinal) {
                throw new IllegalStateException("Class cannot be abstract and final");
            }
            writer.write("abstract ");
        }
        writer.write(this.classType.typeName() + " " + name());
        if (!genericParameters.isEmpty()) {
            writeGenericParameters(writer, combinedTokens, imports);
        }
        writer.write(" ");
        if (superType != null) {
            writer.write("extends ");
            superType.writeComponent(writer, combinedTokens, imports, this.classType);
            writer.write(" ");
        }
        if (!interfaces.isEmpty()) {
            writeClassInterfaces(writer, combinedTokens, imports);
        }
        writer.write("{");
        writer.writeSeparatorLine();
        if (!staticFields.isEmpty()) {
            writeClassFields(staticFields, writer, combinedTokens, imports);
        }
        if (!fields.isEmpty()) {
            writeClassFields(fields, writer, combinedTokens, imports);
        }
        if (!constructors.isEmpty()) {
            writerClassConstructors(writer, combinedTokens, imports);
        }
        if (!staticMethods.isEmpty()) {
            writerClassMethods(staticMethods, writer, combinedTokens, imports);
        }
        if (!methods.isEmpty()) {
            writerClassMethods(methods, writer, combinedTokens, imports);
        }
        if (!innerClasses.isEmpty()) {
            writeInnerClasses(writer, combinedTokens, imports);
        }
        writer.write("\n");
        writer.write("}");
    }

    private void writeGenericParameters(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports)
            throws IOException {
        writer.write("<");
        boolean first = true;
        for (Type parameter : genericParameters) {
            if (first) {
                first = false;
            } else {
                writer.write(", ");
            }
            parameter.writeComponent(writer, declaredTokens, imports, this.classType);
        }
        writer.write(">");
    }

    private void writeClassInterfaces(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports)
            throws IOException {
        if (classType == ClassType.INTERFACE) {
            writer.write("extends ");
        } else {
            writer.write("implements ");
        }
        boolean first = true;
        for (Type interfaceName : interfaces) {
            if (first) {
                first = false;
            } else {
                writer.write(", ");
            }
            interfaceName.writeComponent(writer, declaredTokens, imports, this.classType);
        }
        writer.write(" ");
    }

    private void writeClassFields(Collection<Field> fields,
                                  ModelWriter writer,
                                  Set<String> declaredTokens,
                                  ImportOrganizer imports) throws IOException {
        writer.increasePaddingLevel();
        for (Field field : fields) {
            writer.write("\n");
            field.writeComponent(writer, declaredTokens, imports, this.classType);
        }
        writer.decreasePaddingLevel();
        writer.writeSeparatorLine();
    }

    private void writerClassConstructors(ModelWriter writer,
                                         Set<String> declaredTokens,
                                         ImportOrganizer imports) throws IOException {
        writer.increasePaddingLevel();
        for (Constructor constructor : constructors) {
            writer.write("\n");
            constructor.writeComponent(writer, declaredTokens, imports, this.classType);
            writer.writeSeparatorLine();
        }
        writer.decreasePaddingLevel();
    }

    private void writerClassMethods(List<Method> methods,
                                    ModelWriter writer,
                                    Set<String> declaredTokens,
                                    ImportOrganizer imports) throws IOException {
        writer.increasePaddingLevel();
        for (Method method : methods) {
            writer.write("\n");
            method.writeComponent(writer, declaredTokens, imports, this.classType);
            writer.writeSeparatorLine();
        }
        writer.decreasePaddingLevel();
    }

    private void writeInnerClasses(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports) throws IOException {
        writer.increasePaddingLevel();
        for (InnerClass innerClass : innerClasses) {
            writer.write("\n");
            innerClass.writeComponent(writer, declaredTokens, imports, this.classType);
            writer.writeSeparatorLine();
        }
        writer.decreasePaddingLevel();
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        fields.forEach(field -> field.addImports(imports));
        staticFields.forEach(field -> field.addImports(imports));
        methods.forEach(method -> method.addImports(imports));
        staticMethods.forEach(method -> method.addImports(imports));
        interfaces.forEach(imp -> imp.addImports(imports));
        constructors.forEach(constructor -> constructor.addImports(imports));
        genericParameters.forEach(param -> param.addImports(imports));
        innerClasses.forEach(innerClass -> {
            imports.from(innerClass.imports());
            innerClass.addImports(imports);
        });
        if (superType != null) {
            superType.addImports(imports);
        }
    }

    ClassType classType() {
        return classType;
    }

    /**
     * Fluent API builder for {@link ClassBase}.
     *
     * @param <B> builder type
     * @param <T> built object type
     */
    public abstract static class Builder<B extends Builder<B, T>, T extends ClassBase>
            extends AnnotatedComponent.Builder<B, T> {

        private final Set<Method> methods = new LinkedHashSet<>();
        private final Set<Method> staticMethods = new LinkedHashSet<>();
        private final Set<Type> interfaces = new LinkedHashSet<>();
        private final Map<String, Field> fields = new LinkedHashMap<>();
        private final Map<String, Field> staticFields = new LinkedHashMap<>();
        private final Map<String, InnerClass> innerClasses = new LinkedHashMap<>();
        private final List<Constructor> constructors = new ArrayList<>();
        private final List<TypeArgument> genericParameters = new ArrayList<>();
        private final ImportOrganizer.Builder importOrganizer = ImportOrganizer.builder();
        private ClassType classType = ClassType.CLASS;
        private Type superType;
        private boolean isFinal;
        private boolean isAbstract;
        private boolean isStatic;
        private boolean sortFields = true;
        private boolean sortStaticFields = true;

        Builder() {
        }

        @Override
        public B javadoc(Javadoc javadoc) {
            return super.javadoc(javadoc);
        }

        @Override
        public B addJavadocTag(String tag, String description) {
            return super.addJavadocTag(tag, description);
        }

        @Override
        public B accessModifier(AccessModifier accessModifier) {
            return super.accessModifier(accessModifier);
        }

        /**
         * Whether this type is final.
         *
         * @param isFinal type is abstract
         * @return updated builder instance
         */
        public B isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return identity();
        }

        /**
         * Whether this type is abstract.
         *
         * @param isAbstract type is abstract
         * @return updated builder instance
         */
        public B isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return identity();
        }

        /**
         * Set new super type of this type.
         *
         * @param superType super type of this type
         * @return updated builder instance
         */
        public B superType(Class<?> superType) {
            return superType(TypeName.create(superType));
        }

        /**
         * Set new fully qualified super type name of this type.
         *
         * @param superType super type of this type
         * @return updated builder instance
         */
        public B superType(String superType) {
            return superType(TypeName.create(superType));
        }

        /**
         * Set new super type of this type.
         *
         * @param superType super type of this type
         * @return updated builder instance
         */
        public B superType(TypeName superType) {
            this.superType = Type.fromTypeName(superType);
            return identity();
        }

        /**
         * Add new field to the type.
         *
         * @param consumer field builder consumer
         * @return updated builder instance
         */
        public B addField(Consumer<Field.Builder> consumer) {
            Field.Builder builder = Field.builder();
            consumer.accept(builder);
            return addField(builder.build());
        }

        /**
         * Add new field to the type.
         *
         * @param builder field builder
         * @return updated builder instance
         */
        public B addField(Field.Builder builder) {
            return addField(builder.build());
        }

        /**
         * Add new field to the type.
         *
         * @param field new field
         * @return updated builder instance
         */
        public B addField(Field field) {
            String fieldName = field.name();
            if (field.isStatic()) {
                fields.remove(fieldName);
                staticFields.put(fieldName, field);
            } else {
                staticFields.remove(fieldName);
                fields.put(fieldName, field);
            }
            return identity();
        }

        /**
         * Add new method to the type.
         *
         * @param consumer method builder consumer
         * @return updated builder instance
         */
        public B addMethod(Consumer<Method.Builder> consumer) {
            Method.Builder methodBuilder = Method.builder();
            consumer.accept(methodBuilder);
            return addMethod(methodBuilder);
        }

        /**
         * Add new method to the type.
         *
         * @param builder method builder
         * @return updated builder instance
         */
        public B addMethod(Method.Builder builder) {
            return addMethod(builder.build());
        }

        /**
         * Add new method to the type.
         *
         * @param method new method
         * @return updated builder instance
         */
        public B addMethod(Method method) {
            methods.remove(method);
            staticMethods.remove(method);
            if (method.isStatic()) {
                staticMethods.add(method);
            } else {
                methods.add(method);
            }
            return identity();
        }

        /**
         * Add interface this type should implement.
         *
         * @param interfaceType interface type to implement
         * @return updated builder instance
         */
        public B addInterface(Class<?> interfaceType) {
            if (interfaceType.isInterface()) {
                return addInterface(TypeName.create(interfaceType));
            } else {
                throw new IllegalArgumentException("Provided value needs to be interface, but it was not: "
                                                           + interfaceType.getName());
            }
        }

        /**
         * Add interface this type should implement.
         *
         * @param interfaceName fully qualified interface name to implement
         * @return updated builder instance
         */
        public B addInterface(String interfaceName) {
            return addInterface(TypeName.create(interfaceName));
        }

        /**
         * Add interface this type should implement.
         *
         * @param interfaceType interface to implement
         * @return updated builder instance
         */
        public B addInterface(TypeName interfaceType) {
            interfaces.add(Type.fromTypeName(interfaceType));
            return identity();
        }

        /**
         * Add new inner type to this type.
         *
         * @param consumer inner class builder consumer
         * @return updated builder instance
         */
        public B addInnerClass(Consumer<InnerClass.Builder> consumer) {
            InnerClass.Builder innerClassBuilder = InnerClass.builder();
            consumer.accept(innerClassBuilder);
            return addInnerClass(innerClassBuilder);
        }

        /**
         * Add new inner type to this type.
         *
         * @param supplier inner class builder supplier
         * @return updated builder instance
         */
        public B addInnerClass(Supplier<InnerClass> supplier) {
            return addInnerClass(supplier.get());
        }

        /**
         * Add new inner type to this type.
         *
         * @param innerClass inner class instance
         * @return updated builder instance
         */
        public B addInnerClass(InnerClass innerClass) {
            this.innerClasses.put(innerClass.name(), innerClass);
            return identity();
        }

        /**
         * Add new constructor to this class.
         *
         * @param constructor constructor builder
         * @return updated builder instance
         */
        public B addConstructor(Constructor.Builder constructor) {
            constructors.add(constructor.type(name()).build());
            return identity();
        }

        /**
         * Add new constructor to this class.
         *
         * @param consumer constructor builder consumer
         * @return updated builder instance
         */
        public B addConstructor(Consumer<Constructor.Builder> consumer) {
            Constructor.Builder constructorBuilder = Constructor.builder()
                    .type(name());
            consumer.accept(constructorBuilder);
            constructors.add(constructorBuilder.build());
            return identity();
        }

        /**
         * Add generic argument type.
         *
         * @param typeArgument generic argument type
         * @return updated builder instance
         */
        public B addGenericArgument(TypeArgument typeArgument) {
            this.genericParameters.add(typeArgument);
            return addGenericToken(typeArgument.token(), typeArgument.description());
        }

        /**
         * Add generic argument type.
         *
         * @param consumer generic argument type builder consumer
         * @return updated builder instance
         */
        public B addGenericArgument(Consumer<TypeArgument.Builder> consumer) {
            TypeArgument.Builder tokenBuilder = TypeArgument.builder();
            consumer.accept(tokenBuilder);
            return addGenericArgument(tokenBuilder.build());
        }

        /**
         * Add specific class to be imported.
         *
         * @param typeImport type to be included among imports
         * @return updated builder instance
         */
        public B addImport(Class<?> typeImport) {
            importOrganizer.addImport(typeImport);
            return identity();
        }

        /**
         * Add specific fully qualified type name to be imported.
         *
         * @param importName type to be included among imports
         * @return updated builder instance
         */
        public B addImport(String importName) {
            importOrganizer.addImport(importName);
            return identity();
        }

        /**
         * Add specific fully qualified type name to be imported.
         *
         * @param typeName type to be included among imports
         * @return updated builder instance
         */
        public B addImport(TypeName typeName) {
            importOrganizer.addImport(typeName);
            return identity();
        }

        /**
         * Add specific static import.
         *
         * @param staticImport fully qualified static import name
         * @return updated builder instance
         */
        public B addStaticImport(String staticImport) {
            importOrganizer.addStaticImport(staticImport);
            return identity();
        }

        /**
         * Type of the Java type we are creating.
         * For example: class, interface etc.
         *
         * @param classType Java type
         * @return updated builder instance
         */
        public B classType(ClassType classType) {
            this.classType = classType;
            return identity();
        }

        /**
         * Type of the Java type we are creating.
         * For example: class, interface etc.
         *
         * @param kind the element kind, must be a supported top level type
         * @return updated builder instance
         * @throws java.lang.IllegalArgumentException in case the kind is not supported
         */
        public B classType(ElementKind kind) {
            return switch (kind) {
                case CLASS -> classType(ClassType.CLASS);
                case INTERFACE -> classType(ClassType.INTERFACE);
                default -> throw new IllegalArgumentException("Top level class is not supported for kind: " + kind);
            };
        }

        /**
         * Whether to sort non-static fields by type and name (defaults to {@code true}).
         * If set to {@code false}, fields are ordered by insertion sequence.
         *
         * @param sort whether to sort fields
         * @return updated builder instance
         */
        public B sortFields(boolean sort) {
            this.sortFields = sort;
            return identity();
        }

        /**
         * Whether to sort static fields by type and name (defaults to {@code true}).
         * If set to {@code false}, fields are ordered by insertion sequence.
         *
         * @param sort whether to sort fields
         * @return updated builder instance
         */
        public B sortStaticFields(boolean sort) {
            this.sortStaticFields = sort;
            return identity();
        }

        /**
         * Whether this type is static.
         *
         * @param isStatic whether type is static
         * @return updated builder instance
         */
        B isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return identity();
        }

        ImportOrganizer.Builder importOrganizer() {
            return importOrganizer;
        }
    }
}
