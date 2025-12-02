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

package io.helidon.declarative.codegen.metrics;

import java.util.List;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.RunLevels;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.DeclarativeTypes.WEIGHT;
import static io.helidon.declarative.codegen.metrics.MetricsExtension.GENERATOR;
import static io.helidon.declarative.codegen.metrics.MetricsExtension.addTagsToBuilder;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.GAUGE;
import static io.helidon.declarative.codegen.metrics.MetricsTypes.METER_REGISTRY;

class GaugeHandler {
    private final RegistryRoundContext ctx;

    GaugeHandler(RegistryRoundContext ctx) {
        this.ctx = ctx;
    }

    void handle(TypeInfo typeInfo, List<MetricsExtension.Gauge> gauges) {
        TypeName serviceType = typeInfo.typeName();
        String className = serviceType.className() + "__GaugeRegistrar";

        TypeName generatedType = TypeName.builder()
                .packageName(serviceType.packageName())
                .className(className)
                .build();

        // @Injection.RunLevel(120D)
        Annotation runLevel = Annotation.builder()
                .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_RUN_LEVEL)
                .putValue("value", RunLevels.METRICS)
                .build();

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, GENERATOR, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, GENERATOR, generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .addAnnotation(runLevel)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        // use the same weight
        typeInfo.findAnnotation(WEIGHT)
                .ifPresent(classModel::addAnnotation);

        // add service dependency and fields
        addConstructorAndFields(classModel, serviceType, gauges.size());
        // register each gauge
        addPostConstruct(classModel, gauges);
        // unregister each gauge
        addPreDestroy(classModel, gauges.size());
        // and a nice toString()
        addToString(classModel, serviceType);

        ctx.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void addToString(ClassModel.Builder classModel, TypeName serviceType) {
        classModel.addMethod(toString -> toString
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"Gauge Registrar for ")
                .addContent(serviceType.fqName())
                .addContentLine("\";")
        );
    }

    private void addPreDestroy(ClassModel.Builder classModel,
                               int gaugeCount) {
        Method.Builder preDestroy = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY))
                .name("preDestroy")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        preDestroy.addContentLine("var meters = meterRegistrySupplier.get();");

        for (int i = 0; i < gaugeCount; i++) {
            preDestroy.addContent("meters.remove(this.gauge_")
                    .addContent(String.valueOf(i))
                    .addContentLine(");");
        }

        classModel.addMethod(preDestroy);
    }

    private void addPostConstruct(ClassModel.Builder classModel,
                                  List<MetricsExtension.Gauge> gauges) {
        Method.Builder postConstruct = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT))
                .name("postConstruct")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        postConstruct.addContentLine("var meters = meterRegistrySupplier.get();")
                .addContentLine();

        for (int i = 0; i < gauges.size(); i++) {
            MetricsExtension.Gauge gauge = gauges.get(i);
            addRegisterGauge(postConstruct, gauge, i);
        }

        classModel.addMethod(postConstruct);
    }

    private void addRegisterGauge(Method.Builder postConstruct, MetricsExtension.Gauge gauge, int index) {
        /*
        this.gauge_1 = meters.getOrCreate(Gauge.builder("TestEndpoint.gaugeValue",
                                                                   () -> endpoint.get().gaugeValue())
                                                             .description("Gauge annotation on method gaugeValue()")
                                                             .tags(tags)
                                                             .baseUnit("bytes")
                                                             .scope("application"));

            serviceSupplier
         */
        postConstruct.addContent("this.")
                .addContent("gauge_" + index)
                .addContent(" = meters.getOrCreate(")
                .addContent(GAUGE)
                .addContent(".builder(")
                .addContentLiteral(gauge.name())
                .addContent(", () -> serviceSupplier.get().")
                .addContent(gauge.methodName())
                .addContentLine("())")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".description(")
                .addContentLiteral(gauge.description())
                .addContentLine(")")
                .addContent(".scope(")
                .addContentLiteral(gauge.scope())
                .addContentLine(")")
                .addContent(".baseUnit(")
                .addContentLiteral(gauge.unit())
                .addContentLine(")");

        addTagsToBuilder(postConstruct, gauge.tags());

        postConstruct.decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine(");");

    }

    private void addConstructorAndFields(ClassModel.Builder classModel, TypeName serviceType, int gaugesCount) {
        TypeName serviceSupplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(serviceType)
                .build();
        TypeName meterRegistrySupplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(METER_REGISTRY)
                .build();

        classModel.addField(service -> service
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .type(serviceSupplierType)
                        .name("serviceSupplier"))
                .addField(meterRegistry -> meterRegistry
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .name("meterRegistrySupplier")
                        .type(meterRegistrySupplierType));

        for (int i = 0; i < gaugesCount; i++) {
            final int index = i;
            classModel.addField(service -> service
                    .accessModifier(AccessModifier.PRIVATE)
                    .isVolatile(true)
                    .type(GAUGE)
                    .name("gauge_" + index));
        }

        classModel.addConstructor(ctr -> ctr
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(meterRegistry -> meterRegistry
                        .name("meterRegistrySupplier")
                        .type(meterRegistrySupplierType)
                )
                .addParameter(service -> service
                        .type(serviceSupplierType)
                        .name("serviceSupplier"))
                .addContentLine("this.meterRegistrySupplier = meterRegistrySupplier;")
                .addContentLine("this.serviceSupplier = serviceSupplier;"));
    }
}
