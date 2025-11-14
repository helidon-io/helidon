package io.helidon.builder.codegen.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.codegen.GeneratedMethod;
import io.helidon.builder.codegen.OptionInfo;
import io.helidon.builder.codegen.OptionMethodType;
import io.helidon.builder.codegen.PrototypeInfo;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;

/**
 * Extension to modify the builder and prototype that is generated.
 */
public interface BuilderCodegenExtension {
    /**
     * An extension can update the prototype information, add annotations to types, add custom methods etc.
     * <p>
     * NOTE: this method CANNOT modify options
     *
     * @param prototypeInfo prototype information from blueprint and previous extensions
     * @return updated prototype information
     */
    default PrototypeInfo prototypeInfo(PrototypeInfo prototypeInfo) {
        return prototypeInfo;
    }

    /**
     * A list of (updated) options to be used during code generation.
     * <p>
     * An option is a property of the prototype, that has a prototype accessor, builder field, builder setter,
     * and builder getter. All of these methods will be code generated. If an option is inherited from a blueprint,
     * it is still code generated, with {@link java.lang.Override} annotation.
     *
     * @return list of builder options
     */
    default List<OptionInfo> options(List<OptionInfo> existingOptions) {
        return existingOptions;
    }

    /**
     * Update the prototype interface.
     * This can add additional factory methods, constants, etc.
     * <p>
     * Do not add properties through this method, use {@link #options(java.util.List)} instead.
     *
     * @param classModel prototype interface class model
     */
    default void updatePrototype(ClassModel.Builder classModel) {
    }

    /**
     * Update the builder base.
     * This can add additional fields, methods, constants, etc.
     * <p>
     * Do not add properties through this method, use {@link #options(java.util.List)} instead.
     *
     * @param classModel builder base class model
     */
    default void updateBuilderBase(ClassModel.Builder classModel) {
    }

    /**
     * Update the {@code preBuildPrototype} method of builder base.
     * This method is called first in the builder hierarchy to handle decorators.
     *
     * @param method method builder
     */
    default void updatePreBuildPrototype(Method.Builder method) {
    }

    /**
     * Update the {@code validatePrototype} method of builder base.
     * This method is called last in the builder hierarchy to handle validation, right before
     * calling the {@code build} method.
     *
     * @param method method builder
     */
    default void updateValidatePrototype(Method.Builder method) {
    }

    /**
     * Update the builder.
     *
     * @param classModel builder class model
     */
    default void updateBuilder(ClassModel.Builder classModel) {
    }

    /**
     * Update the implementation class.
     * <p>
     * Do not add properties through this method, use {@link #options(java.util.List)} instead.
     *
     * @param classModel implementation class model
     */
    default void updateImplementation(ClassModel.Builder classModel) {
    }

    /**
     * This allows modification to the methods generated for options by the default builder code generator.
     * To add additional methods to any of the generated code, use
     * {@link #prototypeInfo(io.helidon.builder.codegen.PrototypeInfo)}.
     * <p>
     * <strong>Important note:</strong> we may add new method types in minor versions of Helidon, please make sure
     * this would not break your extension.
     *
     * @param method     method to modify, possibly remove, or return
     * @param methodType type of the method being processed
     * @return update generated method
     */
    Optional<GeneratedMethod> method(GeneratedMethod method, OptionMethodType methodType);
}
