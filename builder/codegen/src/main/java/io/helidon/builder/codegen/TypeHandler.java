package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

interface TypeHandler {

    static TypeHandler create(PrototypeInfo prototypeInfo, OptionInfo option) {
        var declaredType = option.declaredType();

        if (declaredType.isOptional()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.OPTIONAL, 1);
            return new TypeHandlerOptional(prototypeInfo, option);
        }
        if (declaredType.isSupplier()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.SUPPLIER, 1);
            return new TypeHandlerSupplier(prototypeInfo, option);
        }
        if (declaredType.isSet()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.SET, 1);
            return new TypeHandlerSet(prototypeInfo, option);
        }
        if (declaredType.isList()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.LIST, 1);
            return new TypeHandlerList(prototypeInfo, option);
        }
        if (declaredType.isMap()) {
            checkTypeArgsSizeAndTypes(prototypeInfo, option, TypeNames.MAP, 2);
            return new TypeHandlerMap(prototypeInfo, option);
        }
        return new TypeHandlerBase(prototypeInfo, option, option.declaredType());
    }

    /**
     * Create a field for the handled option.
     *
     * @param isBuilder whether we are generating builder field ({@code true}), or implementation field ({@code false})
     * @return a field builder to add to the correct inner class
     */
    Field.Builder field(boolean isBuilder);

    /**
     * The type name we use for builder fields. For {@link java.util.Optional}, {@link java.util.Set} and {@link java.util.List},
     * this would be the first type argument.
     *
     * @return type
     */
    TypeName type();

    /**
     * Add builder base setters for this option.
     *
     * @param classBuilder builder of the inner class {@code BuilderBase}
     * @param returnType   return type of the setter (i.e. {@code BUILDER})
     */
    void setters(InnerClass.Builder classBuilder, TypeName returnType);

    /**
     * Generate body of the builder getter.
     *
     * @param method method builder
     */
    void builderGetterBody(Method.Builder method);

    /**
     * Return type of the builder getter.
     *
     * @return type of the builder getter
     */
    TypeName builderGetterType();

    /**
     * Generate from config section for this option.
     * The config instance is provided under name {@code config}.
     *
     * @param builder          method builder
     * @param optionConfigured configured information for this option
     */
    void generateFromConfig(Method.Builder builder, OptionConfigured optionConfigured);

    /**
     * Whether the builder getter returns an {@link java.util.Optional}.
     *
     * @return if return optional from builder getter
     */
    boolean builderGetterOptional();

    private static void checkTypeArgsSizeAndTypes(PrototypeInfo prototypeInfo,
                                                  OptionInfo option,
                                                  TypeName declaredType,
                                                  int expectedTypeArgs) {
        Object originatingElement;
        if (option.blueprintMethod().isPresent()) {
            originatingElement = option.blueprintMethod().get().originatingElementValue();
        } else {
            originatingElement = prototypeInfo.blueprint().originatingElementValue();
        }
        List<TypeName> typeNames = option.declaredType().typeArguments();
        if (typeNames.size() != expectedTypeArgs) {
            throw new CodegenException("Option of type " + declaredType.fqName() + " must have " + expectedTypeArgs
                                               + " type arguments defined, but option \"" + option.name() + "\" does not",
                                       originatingElement);
        }
        for (TypeName typeName : typeNames) {
            if (typeName.wildcard()) {
                throw new CodegenException("Property of type " + option.declaredType().resolvedName()
                                                   + " is not supported for builder,"
                                                   + " as wildcards cannot be handled correctly in setters",
                                           originatingElement);
            }
        }
    }
}
