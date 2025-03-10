package io.helidon.service.codegen;

import java.util.HashMap;
import java.util.Map;
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
    private final Map<TypeName, Map<String, Constants>> constants = new HashMap<>();
    private final Map<String, TypeName> fields = new HashMap<>();

    FieldHandlerImpl(ClassModel.Builder classModel, Constructor.Builder constructor) {
        this.classModel = classModel;
        this.constructor = constructor;
    }

    @Override
    public String constant(String constantNamePrefix,
                           TypeName constantType,
                           Object uniqueIdentifier,
                           Consumer<ContentBuilder<?>> contentBuilder) {
        return constants.computeIfAbsent(constantType, it -> new HashMap<>())
                .computeIfAbsent(constantNamePrefix, it -> new Constants(constantNamePrefix, classModel, constantType))
                .constant(uniqueIdentifier, contentBuilder);
    }

    @Override
    public void field(TypeName typeName,
                      String fieldName,
                      AccessModifier modifier,
                      Consumer<ContentBuilder<?>> fieldUpdater,
                      Consumer<Constructor.Builder> constructorUpdater) {
        TypeName existing = fields.get(fieldName);
        if (existing == null) {
            addField(typeName, fieldName, modifier, fieldUpdater, constructorUpdater);
            fields.put(fieldName, typeName);
        } else {
            if (!typeName.equals(existing)) {
                throw new CodegenException("Attempting to create a field \"" + fieldName + "\" with different types."
                                                   + "Existing: " + existing.fqName() + ", new: " + typeName.fqName());
            }
        }
    }

    private void addField(TypeName typeName,
                          String fieldName,
                          AccessModifier modifier,
                          Consumer<ContentBuilder<?>> fieldUpdater,
                          Consumer<Constructor.Builder> constructorUpdater) {
        classModel.addField(field -> field
                .name(fieldName)
                .accessModifier(modifier)
                .isFinal(true)
                .type(typeName)
                .update(fieldUpdater::accept));
        constructorUpdater.accept(constructor);
    }

    private static class Constants {
        private final String constantNamePrefix;
        private final ClassModel.Builder classModel;
        private final TypeName typeName;
        private final Map<Object, String> existingConstants = new HashMap<>();
        private int counter;

        private Constants(String constantNamePrefix, ClassModel.Builder classModel, TypeName typeName) {
            this.constantNamePrefix = constantNamePrefix;
            this.classModel = classModel;
            this.typeName = typeName;
        }

        private String constant(Object uniqueIdentifier, Consumer<ContentBuilder<?>> contentBuilder) {
            String constantName = existingConstants.get(uniqueIdentifier);
            if (constantName != null) {
                return constantName;
            }

            String newConstantName = counter == 0 ? constantNamePrefix : constantNamePrefix + "_" + counter;
            counter++;
            existingConstants.put(uniqueIdentifier, newConstantName);

            classModel.addField(newConstant -> newConstant
                    .name(newConstantName)
                    .type(typeName)
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .update(contentBuilder::accept)
            );

            return newConstantName;
        }
    }
}
