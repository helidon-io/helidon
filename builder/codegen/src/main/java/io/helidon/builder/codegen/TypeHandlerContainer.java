package io.helidon.builder.codegen;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.codegen.CodegenUtil.capitalize;

abstract class TypeHandlerContainer extends TypeHandlerBasic {

    TypeHandlerContainer(List<BuilderCodegenExtension> extensions,
                         PrototypeInfo prototypeInfo,
                         OptionInfo option,
                         TypeName type) {
        super(extensions, prototypeInfo, option, type);
    }

    @Override
    public final void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        if (isBuilder) {
            // this field is not visible outside the builder, so no need to add it as an option
            classBuilder.addField(mutated -> mutated
                    .accessModifier(AccessModifier.PRIVATE)
                    .type(TypeNames.PRIMITIVE_BOOLEAN)
                    .name(isMutatedField())
            );
        }
        addFields(classBuilder, isBuilder);
    }

    @Override
    public void fromBuilderAssignment(ContentBuilder<?> contentBuilder) {
        /*
            If this builder's container HAS been mutated, add the other builder's values ONLY if they are not defaults.

            If this builder's container HAS NOT been mutated, set them to the other builder's
            values regardless of whether they are defaulted or explicitly set.

            Generated code:

            if (isXxxMutated) {
                if (builder.isXxxMutated) {
                    addXxx(builder.xxx());
                }
            } else {
                xxx(builder.xxx());
            }
         */
        contentBuilder.addContent("if (this.")
                .addContent(isMutatedField())
                .addContentLine(") {")
                .addContent("if (builder.")
                .addContent(isMutatedField())
                .addContentLine(") {")
                .addContent("add")
                .addContent(capitalize(option().name()))
                .addContent("(builder.")
                .addContent(option().getterName())
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("} else {")
                .addContent(option().setterName())
                .addContent("(builder.")
                .addContent(option().getterName())
                .addContentLine(");")
                .addContentLine("}");
    }

    @Override
    public void fromPrototypeAssignment(ContentBuilder<?> contentBuilder) {
        contentBuilder.addContent("if (!this.")
                .addContent(isMutatedField())
                .addContentLine(") {")
                .addContent("this.")
                .addContent(option().name())
                .addContentLine(".clear();")
                .addContentLine("}")
                .addContent("add")
                .addContent(capitalize(option().name()))
                .addContent("(prototype.")
                .addContent(option().getterName())
                .addContentLine("());");
    }

    @Override
    Optional<GeneratedMethod> prepareSetterConsumer(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterCharArray(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    @Override
    Optional<GeneratedMethod> prepareSetterSupplier(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    void extraSetterContent(ContentBuilder<?> builder) {
        builder.addContentLine("this." + isMutatedField() + " = true;");
    }

    void extraAdderContent(ContentBuilder<?> builder) {
        builder.addContentLine("this." + isMutatedField() + " = true;");
    }

    abstract void addFields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder);

    String isMutatedField() {
        return "is" + capitalize(option().name()) + "Mutated";
    }
}
