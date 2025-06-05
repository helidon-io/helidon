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

package io.helidon.declarative.codegen.faulttolerance;

import java.util.function.Predicate;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.DeclarativeTypes.WEIGHT;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.BULKHEAD;
import static io.helidon.declarative.codegen.faulttolerance.FtTypes.BULKHEAD_ANNOTATION;

final class BulkheadHandler extends FtHandler {

    BulkheadHandler(RegistryCodegenContext ctx) {
        super(ctx, BULKHEAD_ANNOTATION);
    }

    @Override
    void process(RegistryRoundContext roundContext,
                 TypeInfo enclosingType,
                 TypedElementInfo element,
                 Annotation annotation,
                 TypeName generatedType,
                 ClassModel.Builder classModel) {
        TypeName enclosingTypeName = enclosingType.typeName();

        // class definition
        classModel.superType(FtTypes.BULKHEAD_GENERATED_METHOD)
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putValue("value", InterceptorWeights.WEIGHT_BULKHEAD)
                                       .build());

        // generate the class body
        bulkheadBody(classModel,
                     enclosingTypeName,
                     element,
                     generatedType,
                     element.elementName(),
                     annotation);

        // add type to context
        addType(roundContext,
                generatedType,
                classModel,
                enclosingTypeName,
                element);
    }

    private void bulkheadBody(ClassModel.Builder classModel,
                              TypeName enclosingTypeName,
                              TypedElementInfo element,
                              TypeName generatedType,
                              String methodName,
                              Annotation annotation) {

        classModel.addField(bulkhead -> bulkhead
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(BULKHEAD)
                .name("bulkhead"));

        String name = annotation.stringValue("name").filter(Predicate.not(String::isBlank))
                .orElse(null);

        /*
        Constructor (may inject named Bulkhead)
         */
        var ctr = Constructor.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        if (name == null) {
            ctr.addContentLine("this.bulkhead = produceBulkhead();");
        } else {
            // named, inject
            ctr.addParameter(namedBulkhead -> namedBulkhead
                            .name("namedBulkhead")
                            .type(TypeName.builder()
                                          .from(TypeNames.OPTIONAL)
                                          .addTypeArgument(BULKHEAD)
                                          .build())
                            .addAnnotation(namedAnnotation(name)))
                    .addContent("this.bulkhead = namedBulkhead.orElseGet(")
                    .addContent(generatedType)
                    .addContentLine("::produceBulkhead);");
        }

        classModel.addConstructor(ctr);

        /*
        Bulkhead method (implementing abstract method)
         */
        classModel.addMethod(bulkhead -> bulkhead
                .name("bulkhead")
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(BULKHEAD)
                .accessModifier(AccessModifier.PROTECTED)
                .addContentLine("return bulkhead;")
        );

        /*
        Produce bulkhead method (from annotation values)
         */
        String customName;
        if (name == null) {
            customName = enclosingTypeName.fqName()
                    + "." + element.signature().text();
        } else {
            // as the named instance was not found, use the name and our unique signature
            customName = name + "-" + enclosingTypeName.fqName()
                    + "." + element.signature().text();
        }

        classModel.addMethod(produceBulkhead -> produceBulkhead
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(BULKHEAD)
                .name("produceBulkhead")
                .update(builder -> produceBulkheadMethodBody(enclosingTypeName,
                                                             element,
                                                             builder,
                                                             annotation,
                                                             customName))
        );
    }

    private void produceBulkheadMethodBody(TypeName typeName,
                                           TypedElementInfo element,
                                           Method.Builder builder,
                                           Annotation annotation,
                                           String customName) {

        int limit = annotation.intValue("limit").orElse(10);
        int queueLength = annotation.intValue("queueLength").orElse(10);

        builder.addContent("return ")
                .addContent(BULKHEAD)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".name(\"")
                .addContent(customName)
                .addContentLine("\")")
                .addContent(".queueLength(")
                .addContent(String.valueOf(queueLength))
                .addContentLine(")")
                .addContent(".limit(")
                .addContent(String.valueOf(limit))
                .addContentLine(")")
                .addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }
}
