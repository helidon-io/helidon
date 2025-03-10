package io.helidon.service.codegen;

import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

/**
 * An element capable of handling constants on a type.
 */
public interface FieldHandler {
    /**
     * Create a new constant handler for a class.
     * The constant handler ignores existing constants, or constants created outside of it.
     *
     * @param classModel class model builder
     * @return a new constant handler
     */
    static FieldHandler create(ClassModel.Builder classModel, Constructor.Builder constructor) {
        return new FieldHandlerImpl(classModel, constructor);
    }

    /**
     * Creates (or re-uses) a private constant in the generated class.
     * <p>
     * In case a new constant is needed, and there is a name conflict, a number will be used to uniquely
     * identify the constant.
     *
     * @param constantNamePrefix prefix for the constant name, such as {@code HEADER_X_TEST}
     * @param constantType       type of the constant, such as type of {@code HeaderName}
     * @param uniqueIdentifier   unique identification of the constant within its type, must have correct equals and hashCode
     *                           methods
     * @param contentBuilder     content builder to initialize the constant if it does not yet exist
     * @return constant name
     */
    String constant(String constantNamePrefix,
                    TypeName constantType,
                    Object uniqueIdentifier,
                    Consumer<ContentBuilder<?>> contentBuilder);

    /**
     * Ensure a final field of the type and name exists. In case of conflicts (i.e. the field of the same name exists,
     * but is of a different type) and exception is thrown.
     *
     * @param typeName           type of the field
     * @param fieldName          name of the field
     * @param modifier           modifier of the declaration
     * @param fieldUpdater       consumer of the field content builder (if it is initialized inlined)
     * @param constructorUpdater consumer of the constructor builder (if it is initialized in constructor)
     */
    void field(TypeName typeName,
               String fieldName,
               AccessModifier modifier,
               Consumer<ContentBuilder<?>> fieldUpdater,
               Consumer<Constructor.Builder> constructorUpdater);
}
