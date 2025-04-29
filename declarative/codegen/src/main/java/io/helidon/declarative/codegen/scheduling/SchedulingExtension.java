/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.scheduling;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.CodegenValidator;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.RunLevels;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.WEIGHT;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON_ANNOTATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON_INVOCATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE_ANNOTATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE_INVOCATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

class SchedulingExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(SchedulingExtension.class);

    private final RegistryCodegenContext ctx;

    SchedulingExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Map<TypeName, TypeInfo> types = new HashMap<>();
        for (TypeInfo info : roundContext.types()) {
            types.put(info.typeName(), info);
        }

        Map<TypeName, List<Scheduled>> scheduledByType = new HashMap<>();

        addFixedRate(roundContext, scheduledByType);
        addCron(roundContext, scheduledByType);

        scheduledByType.forEach((type, schedules) -> {
            TypeInfo typeInfo = types.get(type);
            if (typeInfo == null) {
                typeInfo = roundContext.typeInfo(type).orElseThrow(() -> new CodegenException("No type info found for type "
                                                                                                      + type));
            }
            checkTypeIsService(roundContext, typeInfo);
            generateScheduledStarter(roundContext, typeInfo, schedules);
        });
    }

    private void generateScheduledStarter(RegistryRoundContext roundContext, TypeInfo typeInfo, List<Scheduled> schedules) {
        TypeName serviceType = typeInfo.typeName();
        String className = serviceType.className() + "__ScheduledStarter";

        TypeName generatedType = TypeName.builder()
                .packageName(serviceType.packageName())
                .className(className)
                .build();

        // @Injection.RunLevel(70D)
        Annotation runLevel = Annotation.builder()
                .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_RUN_LEVEL)
                .putValue("value", RunLevels.SCHEDULING)
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

        // add service dependency, config, and fields
        addConstructorAndFields(classModel, serviceType, schedules.size());
        // start each scheduler
        addPostConstruct(classModel, serviceType, schedules);
        // stop each future
        addPreDestroy(classModel, schedules.size());
        // and a nice toString()
        addToString(classModel, serviceType);

        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void addToString(ClassModel.Builder classModel, TypeName serviceType) {
        classModel.addMethod(toString -> toString
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return \"ScheduledStarter for ")
                .addContent(serviceType.fqName())
                .addContentLine("\";")
        );
    }

    private void addPreDestroy(ClassModel.Builder classModel,
                               int scheduledSize) {
        Method.Builder postConstruct = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY))
                .name("preDestroy")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        for (int i = 0; i < scheduledSize; i++) {
            postConstruct.addContent("this.task_")
                    .addContent(String.valueOf(i))
                    .addContentLine(".close();");
        }

        classModel.addMethod(postConstruct);
    }

    private void addPostConstruct(ClassModel.Builder classModel,
                                  TypeName serviceType,
                                  List<Scheduled> schedules) {
        Method.Builder postConstruct = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT))
                .name("postConstruct")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        postConstruct.addContentLine("var config = configSupplier.get();")
                .addContent("var classConfig = config.get(\"")
                .addContent(serviceType.fqName())
                .addContentLine("\");")
                .addContentLine("var service = serviceSupplier.get();")
                .addContentLine("");

        for (int i = 0; i < schedules.size(); i++) {
            Scheduled scheduled = schedules.get(i);
            addStartScheduled(postConstruct, scheduled, i);
        }

        classModel.addMethod(postConstruct);
    }

    private void addStartScheduled(Method.Builder postConstruct, Scheduled scheduled, int index) {
        /*
        this.task_1 = FixedRate.builder()
            .task(service::scheduledTask)
            .build();
         */
        postConstruct.addContent("this.")
                .addContent("task_" + index)
                .addContent(" = ");
        scheduled.createScheduledContent(postConstruct);

        // config
        // classConfig
        String configVariable;
        String configKey;
        if (scheduled.configKey().isPresent()) {
            // root config
            configVariable = "config";
            configKey = scheduled.configKey().get();
        } else {
            // class config + method name
            configVariable = "classConfig";
            configKey = scheduled.methodName() + ".schedule";
        }
        postConstruct.increaseContentPadding()
                .increaseContentPadding()
                .addContent(".config(")
                .addContent(configVariable)
                .addContent(".get(\"")
                .addContent(configKey)
                .addContentLine("\"))");

        if (scheduled.hasParameter()) {
            postConstruct
                    .addContent(".task(service::")
                    .addContent(scheduled.methodName())
                    .addContentLine(")");
        } else {
            postConstruct
                    .addContent(".task(sinv -> service.")
                    .addContent(scheduled.methodName())
                    .addContentLine("())");
        }

        postConstruct.addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addConstructorAndFields(ClassModel.Builder classModel, TypeName serviceType, int schedulesSize) {
        TypeName configSupplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(CONFIG)
                .build();
        TypeName serviceSupplierType = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(serviceType)
                .build();

        classModel.addField(service -> service
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .type(serviceSupplierType)
                        .name("serviceSupplier"))
                .addField(configSupplier -> configSupplier
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .name("configSupplier")
                        .type(configSupplierType)
                );

        for (int i = 0; i < schedulesSize; i++) {
            final int index = i;
            classModel.addField(service -> service
                    .accessModifier(AccessModifier.PRIVATE)
                    .isVolatile(true)
                    .type(SchedulingTypes.TASK)
                    .name("task_" + index));
        }

        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(configSupplier -> configSupplier
                        .name("configSupplier")
                        .type(configSupplierType)
                        .name("configSupplier")
                )
                .addParameter(service -> service
                        .type(serviceSupplierType)
                        .name("serviceSupplier"))
                .addContentLine("this.configSupplier = configSupplier;")
                .addContent("this.serviceSupplier = serviceSupplier;"));
    }

    private void processCron(List<Scheduled> allScheduled,
                             TypedElementInfo element) {

        // there can be a method argument, but it must be of correct type
        boolean hasInvocationArgument = checkAndHasArgument(element, CRON_ANNOTATION, CRON_INVOCATION);

        // read annotation values
        // read annotation values
        Annotation annotation = element.annotation(CRON_ANNOTATION);
        String expression = annotation.stringValue().orElse("* * * * * ?"); // required
        boolean concurrent = annotation.booleanValue("concurrent").orElse(true);
        Optional<String> configKey = annotation.stringValue("configKey")
                .filter(Predicate.not(String::isEmpty));

        // add for processing
        allScheduled.add(new Cron(element.elementName(),
                                  hasInvocationArgument,
                                  expression,
                                  concurrent,
                                  configKey));
    }

    private void processFixedRate(List<Scheduled> allScheduled,
                                  TypeName enclosingType,
                                  TypedElementInfo element) {
        // there can be a method argument, but it must be of correct type
        boolean hasInvocationArgument = checkAndHasArgument(element, FIXED_RATE_ANNOTATION, FIXED_RATE_INVOCATION);

        // read annotation values
        Annotation annotation = element.annotation(FIXED_RATE_ANNOTATION);
        String rate = annotation.stringValue().orElse("PT10S"); // required
        String delayBy = annotation.stringValue("delayBy").orElse("PT0S");
        String delayType = annotation.stringValue("delayType").orElse("SINCE_PREVIOUS_START");
        Optional<String> configKey = annotation.stringValue("configKey")
                .filter(Predicate.not(String::isEmpty));

        CodegenValidator.validateDuration(enclosingType, element, FIXED_RATE_ANNOTATION, "interval", rate);
        CodegenValidator.validateDuration(enclosingType, element, FIXED_RATE_ANNOTATION, "delayBy", delayBy);

        // add for processing
        allScheduled.add(new FixedRate(element.elementName(),
                                       hasInvocationArgument,
                                       rate,
                                       delayBy,
                                       delayType,
                                       configKey));
    }

    private boolean checkAndHasArgument(TypedElementInfo element, TypeName annotationType, TypeName invocationArgumentType) {
        List<TypedElementInfo> typedElementInfos = element.parameterArguments();
        if (typedElementInfos.size() == 1
                && typedElementInfos.getFirst().typeName().equals(invocationArgumentType)) {
            return true;
        } else if (typedElementInfos.isEmpty()) {
            return false;
        } else {
            throw new CodegenException("Scheduling methods may have zero or one arguments. The argument for @"
                                               + annotationType.fqName()
                                               + " must be of type " + invocationArgumentType.fqName() + ".",
                                       element.originatingElementValue());
        }
    }

    private void checkTypeIsService(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        Optional<ClassModel.Builder> descriptor = roundContext.generatedType(ctx.descriptorType(typeInfo.typeName()));
        if (descriptor.isEmpty()) {
            throw new CodegenException("Type annotated with one of the scheduling annotations is not a service itself."
                                               + " It must be annotated with "
                                               + SERVICE_ANNOTATION_SINGLETON.classNameWithEnclosingNames() + ".",
                                       typeInfo.originatingElementValue());
        }
    }

    private TypeName enclosingType(TypedElementInfo element) {
        Optional<TypeName> enclosingType = element.enclosingType();
        if (enclosingType.isEmpty()) {
            throw new CodegenException("Element " + element + " does not have an enclosing type",
                                       element.originatingElementValue());
        }
        return enclosingType.get();
    }

    private void addFixedRate(RegistryRoundContext roundContext,
                              Map<TypeName, List<Scheduled>> scheduledByType) {
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(FIXED_RATE_ANNOTATION);
        for (TypedElementInfo element : elements) {
            TypeName enclosingType = enclosingType(element);
            var allScheduled = scheduledByType.computeIfAbsent(enclosingType, k -> new ArrayList<>());
            processFixedRate(allScheduled, enclosingType, element);
        }
    }

    private void addCron(RegistryRoundContext roundContext,
                         Map<TypeName, List<Scheduled>> scheduledByType) {
        var elements = roundContext.annotatedElements(CRON_ANNOTATION);
        for (TypedElementInfo element : elements) {
            TypeName enclosingType = enclosingType(element);
            var allScheduled = scheduledByType.computeIfAbsent(enclosingType, k -> new ArrayList<>());
            processCron(allScheduled, element);
        }

    }

    private interface Scheduled {
        boolean hasParameter();

        String methodName();

        void createScheduledContent(ContentBuilder<?> content);

        Optional<String> configKey();
    }

    private record Cron(String methodName,
                        boolean hasParameter,
                        String expression,
                        boolean concurrent,
                        Optional<String> configKey)
            implements Scheduled {

        @Override
        public void createScheduledContent(ContentBuilder<?> content) {
            content.addContent(CRON)
                    .addContentLine(".builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".expression(\"")
                    .addContent(expression)
                    .addContentLine("\")");

            if (!concurrent) {
                content.addContentLine(".concurrentExecution(false)");
            }

            content.decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private record FixedRate(String methodName,
                             boolean hasParameter,
                             String rate,
                             String delayBy,
                             String delayType,
                             Optional<String> configKey) implements Scheduled {
        @Override
        public void createScheduledContent(ContentBuilder<?> content) {
            content.addContent(FIXED_RATE)
                    .addContentLine(".builder()")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".interval(")
                    .addContent(Duration.class)
                    .addContent(".parse(\"")
                    .addContent(rate)
                    .addContentLine("\"))");

            if (!"PT0S".equals(delayBy)) {
                content.addContent(".delayBy(")
                        .addContent(Duration.class)
                        .addContent(".parse(\"")
                        .addContent(delayBy)
                        .addContentLine("\"))");
            }
            if (!"SINCE_PREVIOUS_START".equals(delayType)) {
                content.addContent(".delayType(")
                        .addContent(SchedulingTypes.FIXED_RATE_DELAY_TYPE)
                        .addContent(".")
                        .addContent(delayType)
                        .addContentLine(")");
            }
            content.decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }
}
