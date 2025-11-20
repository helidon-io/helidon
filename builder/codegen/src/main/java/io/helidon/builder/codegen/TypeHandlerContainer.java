/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
                .addContentLine("());")
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .addContent(option().setterName())
                .addContent("(builder.")
                .addContent(option().getterName())
                .addContentLine("());")
                .addContentLine("}");
    }

    @Override
    public void fromPrototypeAssignment(ContentBuilder<?> contentBuilder) {
        if (option().builderOptionOnly()) {
            return;
        }

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

        if (option().provider().isPresent()) {
            // disable service discovery, as we have copied the value from a prototype
            contentBuilder.addContent("this.")
                    .addContent(option().name() + "DiscoverServices")
                    .addContentLine(" = false;");
        }
    }

    @Override
    Optional<GeneratedMethod> prepareSetterConsumer(Javadoc getterJavadoc) {
        if (option().runtimeType().isEmpty()) {
            return Optional.empty();
        }
        RuntimeTypeInfo runtimeTypeInfo = option().runtimeType().get();
        if (runtimeTypeInfo.factoryMethod().isEmpty()) {
            return Optional.empty();
        }
        // consumer of builder for setter of a collection can only be added
        // if the factory method returns the collection
        FactoryMethod factoryMethod = runtimeTypeInfo.factoryMethod().get();
        if (!(factoryMethod.returnType().isList() || factoryMethod.returnType().isSet())) {
            return Optional.empty();
        }

        return super.prepareSetterConsumer(getterJavadoc);
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterCharArray(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    @Override
    Optional<GeneratedMethod> prepareSetterSupplier(Javadoc getterJavadoc) {
        return Optional.empty();
    }

    @Override
    Optional<GeneratedMethod> prepareSetterPrototypeOfRuntimeType(Javadoc getterJavadoc) {
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
