package io.helidon.service.codegen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

class FieldHandlerImpl implements FieldHandler {
    private final ClassModel.Builder classModel;
    private final Constructor.Builder constructor;

    /*
    // each prefix has a single sequence, a new constant is created for each unique type and unique identifier
    // identifier 42
    private static final String MY_PREFIX = "42";
    private static final String MY_PREFIX_2 = "43";
    // identifier 42, but different type
    private static final Integer MY_PREFIX_3 = 42;
     */
    private final Map<String, PrefixedConstants> constants = new HashMap<>();
    private final Map<String, PrefixedFields> fields = new HashMap<>();

    FieldHandlerImpl(ClassModel.Builder classModel, Constructor.Builder constructor) {
        this.classModel = classModel;
        this.constructor = constructor;
    }

    @Override
    public String constant(String constantNamePrefix,
                           TypeName constantType,
                           Object uniqueIdentifier,
                           Consumer<ContentBuilder<?>> contentBuilder) {
        return constants.computeIfAbsent(constantNamePrefix, it -> new PrefixedConstants(classModel, constantNamePrefix))
                .constant(constantType, uniqueIdentifier, contentBuilder);
    }

    @Override
    public String field(TypeName typeName,
                      String fieldName,
                      AccessModifier modifier,
                      Object uniqueIdentifier,
                      Consumer<ContentBuilder<?>> fieldUpdater,
                      BiConsumer<Constructor.Builder, String> constructorUpdater) {
        return fields.computeIfAbsent(fieldName, it -> new PrefixedFields(classModel, constructor, fieldName))
                .field(typeName, modifier, uniqueIdentifier, fieldUpdater, constructorUpdater);
    }

    private static class PrefixedFields {

        private final ClassModel.Builder classModel;
        private final Constructor.Builder constructor;
        private final String fieldNamePrefix;
        private final AtomicInteger counter = new AtomicInteger();
        private final Map<UniqueIdentifier, String> existingFields = new HashMap<>();
        private final boolean suffixed;

        public PrefixedFields(ClassModel.Builder classModel, Constructor.Builder constructor, String fieldNamePrefix) {
            this.classModel = classModel;
            this.constructor = constructor;
            this.fieldNamePrefix = fieldNamePrefix;
            this.suffixed = fieldNamePrefix.endsWith("_");;
        }

        public String field(TypeName typeName,
                          AccessModifier modifier,
                          Object uniqueIdentifier,
                          Consumer<ContentBuilder<?>> fieldUpdater,
                          BiConsumer<Constructor.Builder, String> constructorUpdater) {
            UniqueIdentifier ui = new UniqueIdentifier(uniqueIdentifier, typeName);
            return existingFields.computeIfAbsent(ui, it -> {
                int nextId = counter.getAndIncrement();
                String name;
                if (nextId == 0 && !suffixed) {
                    name = fieldNamePrefix;
                } else {
                    if (suffixed) {
                        name = fieldNamePrefix + nextId;
                    } else {
                        name = fieldNamePrefix + "_" + nextId;
                    }
                }

                classModel.addField(field -> field
                        .name(name)
                        .accessModifier(modifier)
                        .isFinal(true)
                        .type(typeName)
                        .update(fieldUpdater::accept));
                constructorUpdater.accept(constructor, name);

                return name;
            });
        }
    }


    private static class PrefixedConstants {
        private final ClassModel.Builder classModel;
        private final String constantNamePrefix;
        private final AtomicInteger counter = new AtomicInteger();
        private final Map<UniqueIdentifier, String> existingConstants = new HashMap<>();

        private PrefixedConstants(ClassModel.Builder classModel, String constantNamePrefix) {
            this.classModel = classModel;
            this.constantNamePrefix = constantNamePrefix;
        }

        public String constant(TypeName constantType, Object uniqueIdentifier, Consumer<ContentBuilder<?>> contentBuilder) {
            UniqueIdentifier ui = new UniqueIdentifier(uniqueIdentifier, constantType);
            return existingConstants.computeIfAbsent(ui, it -> {
                int nextId = counter.getAndIncrement();
                String name;
                if (nextId == 0 && !constantNamePrefix.endsWith("_")) {
                    name = constantNamePrefix;
                } else {
                    name = constantNamePrefix + "_" + nextId;
                }

                classModel.addField(newConstant -> newConstant
                        .name(name)
                        .type(constantType)
                        .accessModifier(AccessModifier.PRIVATE)
                        .isStatic(true)
                        .isFinal(true)
                        .update(contentBuilder::accept)
                );

                return name;
            });
        }
    }

    private record UniqueIdentifier(Object uniqueIdentifier, TypeName typeName) {
    }
}
