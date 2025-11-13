package io.helidon.builder.codegen;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;

class TypeHandlerBase implements TypeHandler {
    private final OptionInfo option;
    private final TypeName type;
    private final PrototypeInfo prototypeInfo;

    TypeHandlerBase(PrototypeInfo prototypeInfo, OptionInfo option, TypeName type) {
        this.prototypeInfo = prototypeInfo;
        this.option = option;
        this.type = type;
    }

    protected static TypeName firstTypeArgument(OptionInfo option) {
        // number of type arguments is validated when creating type handler
        return option.declaredType().typeArguments().getFirst();
    }

    static TypeName toWildcard(TypeName typeName) {
        if (typeName.wildcard()) {
            return typeName;
        }
        if (typeName.generic()) {
            return TypeName.builder()
                    .className(typeName.className())
                    .wildcard(true)
                    .build();
        }
        return TypeName.builder(typeName).wildcard(true).build();
    }

    static Optional<FactoryMethod> findFactory(PrototypeInfo prototype, TypeName type) {
        return prototype.factoryMethods()
                .stream()
                .filter(it -> {
                    if (it.parameters().size() == 1) {
                        var param = it.parameters().getFirst();
                        return param.type().equals(CONFIG) || param.type().equals(COMMON_CONFIG);
                    }
                    return false;
                })
                .filter(it -> it.returnType().equals(type))
                .findFirst();

    }

    @Override
    public TypeName type() {
        return type;
    }

    @Override
    public Field.Builder field(boolean isBuilder) {
        var field = Field.builder()
                .name(option().name())
                .isFinal(!isBuilder);

        if (isBuilder && option().required()) {
            // required fields must be nullable, so we can check they were explicitly configured
            field.type(option().declaredType().boxed());
        } else {
            field.type(option().declaredType());
        }

        if (isBuilder && option().defaultValue().isPresent()) {
            option().defaultValue().get().accept(field);
        }

        return field;
    }

    @Override
    public void setters(InnerClass.Builder classBuilder, TypeName returnType) {
        /*
        Setter for declared type: BUILDER username(String), BUILDER password(char[] password)
        Setter for String for char[]: BUILDER password(String password)
        If there is a factory builder for the type: BUILDER something(Consumer<Something.Builder> something)
        If the option is a Builder itself, create a consumer for the builder that does not change instance
         */

        declaredSetter(classBuilder, returnType);
        if (type().equals(Types.CHAR_ARRAY)) {
            charArraySetter(classBuilder, returnType);
        }
        if (option().builderInfo().isPresent()) {
            builderConsumerSetter(classBuilder, returnType, option().builderInfo().get());
            supplierSetter(classBuilder, returnType);
        }
        String fqName = type().fqName();
        if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field, we want to generate consumer (special, same instance)
            setterConsumer(classBuilder, returnType);
        }
    }

    @Override
    public void builderGetterBody(Method.Builder method) {
        method.addContent("return ");
        if (builderGetterOptional()) {
            method.addContent(Optional.class)
                    .addContent(".ofNullable(")
                    .addContent(option().name())
                    .addContent(")");
        } else {
            method.addContent(option().name());
        }
        method.addContentLine(";");
    }

    @Override
    public TypeName builderGetterType() {
        if (builderGetterOptional()) {
            if (option().declaredType().isOptional()) {
                // already wrapped
                return option().declaredType();
            } else {
                return TypeName.builder(TypeNames.OPTIONAL)
                        .addTypeArgument(option().declaredType().boxed())
                        .build();
            }
        }
        return option().declaredType();
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        method.addContent(configGet(optionConfigured));
        String fqName = type().fqName();

        var setterName = option().setter().elementName();
        if (fqName.endsWith(".Builder")) {
            // this is a special case - we have a builder field
            if (option().defaultValue().isPresent()) {
                method.addContent(".as(")
                        .addContent(CONFIG)
                        .addContent(".class).ifPresent(")
                        .addContent(option.name())
                        .addContentLine("::config);");
            } else {
                // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
                int lastDot = fqName.lastIndexOf('.');
                String builderMethod = fqName.substring(0, lastDot) + ".builder()";
                method.addContentLine(".as(" + builderMethod + "::config).ifPresent(this::" + setterName + ");");
            }
        } else {
            Optional<FactoryMethod> factoryMethod = findFactory(prototype(), type());
            if (factoryMethod.isPresent()) {
                generateFromConfig(method, factoryMethod.get());
            } else {
                generateFromConfig(method);
            }

            method.addContentLine(".ifPresent(this::" + setterName + ");");
        }
    }

    @Override
    public String toString() {
        return option().declaredType().fqName() + " " + option().name();
    }

    @Override
    public boolean builderGetterOptional() {
        boolean required = option().required();
        boolean hasDefault = option().defaultValue().isPresent();

        // optional and collections - good return types
        if (option().declaredType().isList()
                || option().declaredType().isMap()
                || option().declaredType().isSet()) {
            return false;
        }
        if (option().declaredType().isOptional()) {
            return true;
        }
        // optional and primitive type - good return type (uses default for primitive if not customized)
        if (!required && option().declaredType().primitive()) {
            return false;
        }
        // has default, and not Optional<X> - return type (never can be null)
        // any other case (required, optional without defaults) - return optional
        return !hasDefault;
    }

    /**
     * Type name to be used in setters.
     * For generic types, this may be {@code List<? extends Type>} rather than {@code List<Type>}.
     *
     * @return type name to be used in generic arguments
     */
    TypeName setterArgumentTypeName() {
        return option.setter().parameterArguments().getFirst().typeName();
    }

    void generateFromConfig(ContentBuilder<?> content) {
        if (type().fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        content.addContent(".as(")
                .addContent(type().boxed().genericTypeName())
                .addContent(".class)");

    }

    void generateFromConfig(ContentBuilder<?> content, FactoryMethod factoryMethod) {
        if (type().fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        content.addContent(".as(")
                .addContent(factoryMethod.declaringType().genericTypeName())
                .addContent("::");
        if (!type().typeArguments().isEmpty()) {
            content.addContent("<");
            Iterator<TypeName> iterator = type().typeArguments().iterator();
            while (iterator.hasNext()) {
                content.addContent(iterator.next());
                if (iterator.hasNext()) {
                    content.addContent(", ");
                }
            }
            content.addContent(">");
        }
        content.addContent(factoryMethod.methodName())
                .addContent(")");
    }

    String configGet(OptionConfigured configured) {
        if (configured.merge()) {
            return "config";
        }
        return "config.get(\"" + configured.configKey() + "\")";
    }

    OptionInfo option() {
        return option;
    }

    PrototypeInfo prototype() {
        return prototypeInfo;
    }

    void declaredSetter(InnerClass.Builder classBuilder, TypeName returnType) {
        TypedElementInfo setter = option().setter();

        Method.Builder builder = Method.builder()
                .name(setter.elementName())
                .returnType(returnType)
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .returnType(returnType)
                .update(it -> setter.annotations().forEach(it::addAnnotation))
                .accessModifier(setter.accessModifier());

        setter.parameterArguments()
                .forEach(it -> builder.addParameter(param -> param.name(it.elementName())
                        .type(setterArgumentTypeName())
                        .update(iit -> it.annotations().forEach(iit::addAnnotation))));

        if (!option().declaredType().primitive()) {
            builder.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + option().name() + ");");
        }

        option().decorator()
                .ifPresent(decorator -> builder.addContent("new ")
                        .addContent(decorator)
                        .addContent("().decorate(this, ")
                        .addContent(option().name())
                        .addContentLine(");"));

        builder.addContentLine("this." + option().name() + " = " + option().name() + ";");

        builder.addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    void setterConsumer(InnerClass.Builder classBuilder, TypeName returnType) {
        TypedElementInfo setter = option().setter();

        TypeName paramType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(setterArgumentTypeName())
                .build();
        Javadoc javadoc = Javadoc.builder(Javadoc.parse(setter.description().orElse("")))
                .parameters(Map.of())
                .addParameter("consumer", "consumer of builder")
                .build();

        Method.Builder builder = Method.builder()
                .name(setter.elementName())
                .returnType(returnType)
                .addParameter(param -> param.name("consumer")
                        .type(paramType))
                .javadoc(javadoc)
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(consumer);")
                .addContent("var builder = ");

        if (option().defaultValue().isPresent()) {
            builder.addContentLine("this." + option().name() + ";");
        } else {
            String fqName = type().fqName();
            // a bit dirty hack - we expect builder() method to exist on the class that owns the builder
            int lastDot = fqName.lastIndexOf('.');
            String builderMethod = fqName.substring(0, lastDot) + ".builder()";
            builder.addContentLine(builderMethod + ";");
        }

        builder.addContentLine("consumer.accept(builder);")
                .addContentLine("this." + setter.elementName() + "(builder);")
                .addContentLine("return self();");
        classBuilder.addMethod(builder);
    }

    boolean isConfigProperty() {
        return "config".equals(option().name())
                && (
                type().equals(CONFIG)
                        || type().equals(COMMON_CONFIG));
    }

    void charArraySetter(InnerClass.Builder classBuilder, TypeName returnType) {
        TypedElementInfo setter = option().setter();

        classBuilder.addMethod(builder -> builder
                .name(setter.elementName())
                .returnType(returnType)
                .addParameter(param -> param.name(option().name())
                        .type(TypeNames.STRING))
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .accessModifier(setter.accessModifier())
                .update(it -> setter.annotations().forEach(it::addAnnotation))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");")
                .addContentLine("this." + option().name() + " = " + option().name() + ".toCharArray();")
                .addContentLine("return self();"));
    }

    void builderConsumerSetter(InnerClass.Builder classBuilder,
                               TypeName returnType,
                               OptionBuilder optionBuilder) {
        TypedElementInfo setter = option().setter();

        TypeName paramType = TypeName.builder()
                .type(Consumer.class)
                .addTypeArgument(optionBuilder.builderType())
                .build();
        Javadoc javadoc = Javadoc.builder(Javadoc.parse(setter.description().orElse("")))
                .parameters(Map.of())
                .addParameter("consumer", "consumer of builder")
                .build();

        Method.Builder builder = Method.builder()
                .name(setter.elementName())
                .returnType(returnType)
                .javadoc(javadoc)
                .accessModifier(setter.accessModifier())
                .addParameter(param -> param.name("consumer")
                        .type(paramType))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(consumer);")
                .addContent("var builder = ");

        if (optionBuilder.builderMethodName().equals("<init>")) {
            builder.addContent("new ")
                    .addContent(optionBuilder.builderType())
                    .addContentLine("();");
        } else {
            builder.addContent(type())
                    .addContentLine("." + optionBuilder.builderMethodName() + "();");
        }

        builder.addContentLine("consumer.accept(builder);")
                .addContent("this." + setter.elementName() + "(builder.")
                .addContent(optionBuilder.buildMethodName())
                .addContentLine("());")
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }

    void supplierSetter(InnerClass.Builder classBuilder,
                        TypeName returnType) {
        TypedElementInfo setter = option().setter();
        TypeName supplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(toWildcard(setterArgumentTypeName()))
                .build();

        Javadoc javadoc = Javadoc.builder(Javadoc.parse(setter.description().orElse("")))
                .parameters(Map.of())
                .addParameter("supplier", "supplier of value, such as a {@link io.helidon.common.Builder}")
                .build();

        Method.Builder builder = Method.builder()
                .name(setter.elementName())
                .returnType(returnType)
                .javadoc(javadoc)
                .accessModifier(setter.accessModifier())
                .addParameter(param -> param.name("supplier")
                        .type(supplierType))
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(supplier);")
                .addContent("this.")
                .addContent(setter.elementName())
                .addContentLine("(supplier.get());")
                .addContentLine("return self();");

        classBuilder.addMethod(builder);
    }
}
