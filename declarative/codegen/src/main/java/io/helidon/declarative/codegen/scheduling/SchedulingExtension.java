/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.CodegenValidator;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
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

import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON_ANNOTATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.CRON_INVOCATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE_ANNOTATION;
import static io.helidon.declarative.codegen.scheduling.SchedulingTypes.FIXED_RATE_INVOCATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON;

class SchedulingExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(SchedulingExtension.class);
    private static final AtomicInteger TYPE_COUNTER = new AtomicInteger();

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

        List<Scheduled> allScheduled = new ArrayList<>();
        Collection<TypedElementInfo> elements = roundContext.annotatedElements(FIXED_RATE_ANNOTATION);
        AtomicInteger scheduledCounter = new AtomicInteger();

        for (TypedElementInfo element : elements) {
            TypeInfo typeInfo = enclosingTypeInfo(types, element);
            processFixedRate(roundContext, allScheduled, typeInfo, element, scheduledCounter);
        }
        elements = roundContext.annotatedElements(CRON_ANNOTATION);
        for (TypedElementInfo element : elements) {
            TypeInfo typeInfo = enclosingTypeInfo(types, element);
            processCron(roundContext, allScheduled, typeInfo, element, scheduledCounter);
        }

        generateScheduledStarter(roundContext, allScheduled);
    }

    private void generateScheduledStarter(RegistryRoundContext roundContext, List<Scheduled> allScheduled) {
        if (allScheduled.isEmpty()) {
            return;
        }

        int typeCounter = TYPE_COUNTER.getAndIncrement();
        String className = "Declarative__ScheduledStarter";
        if (typeCounter != 0) {
            className = className + "_" + typeCounter;
        }
        TypeName generatedType = TypeName.builder()
                .packageName(packageName(allScheduled))
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

        Map<TypeName, String> serviceToFields = new HashMap<>();
        collectServiceTypes(serviceToFields, allScheduled);

        // add service and futures fields
        addFields(classModel, serviceToFields, allScheduled);
        // add service dependency for each service
        addConstructor(classModel, serviceToFields);
        // start each scheduler
        addPostConstruct(classModel, serviceToFields, allScheduled);
        // stop each future
        addPreDestroy(classModel, serviceToFields, allScheduled);
        // and a nice toString()
        addToString(classModel, serviceToFields);

        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void addToString(ClassModel.Builder classModel, Map<TypeName, String> serviceToFields) {
        classModel.addMethod(toString -> toString
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addContent("return \"ScheduledStarter for ")
                .addContent(serviceToFields.keySet()
                                    .stream()
                                    .map(TypeName::fqName)
                                    .collect(Collectors.joining(", ")))
                .addContentLine("\";")
        );
    }

    private void addPreDestroy(ClassModel.Builder classModel,
                               Map<TypeName, String> serviceToFields,
                               List<Scheduled> allScheduled) {
        Method.Builder postConstruct = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY))
                .name("preDestroy")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        for (Scheduled scheduled : allScheduled) {
            postConstruct.addContent("this.task_")
                    .addContent(String.valueOf(scheduled.index()))
                    .addContentLine(".close();");
        }

        classModel.addMethod(postConstruct);
    }

    private void addPostConstruct(ClassModel.Builder classModel,
                                  Map<TypeName, String> serviceToFields,
                                  List<Scheduled> allScheduled) {
        Method.Builder postConstruct = Method.builder()
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_POST_CONSTRUCT))
                .name("postConstruct")
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        for (Scheduled scheduled : allScheduled) {
            addStartScheduled(postConstruct, serviceToFields, scheduled);
        }

        classModel.addMethod(postConstruct);
    }

    private void addStartScheduled(Method.Builder postConstruct, Map<TypeName, String> serviceToFields, Scheduled scheduled) {
        /*
        this.task_1 = FixedRate.builder()
            .task(service_1::scheduledTask)
            .build();
         */
        postConstruct.addContent("this.")
                .addContent("task_" + scheduled.index())
                .addContent(" = ");
        scheduled.createScheduledContent(postConstruct);

        if (scheduled.hasParameter()) {
            postConstruct.increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".task(")
                    .addContent(serviceToFields.get(scheduled.serviceType()))
                    .addContent("::")
                    .addContent(scheduled.methodName())
                    .addContentLine(")");
        } else {
            postConstruct.increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(".task(sinv -> ")
                    .addContent(serviceToFields.get(scheduled.serviceType()))
                    .addContent(".")
                    .addContent(scheduled.methodName())
                    .addContentLine("())");
        }

        postConstruct.addContentLine(".build();")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addConstructor(ClassModel.Builder classModel, Map<TypeName, String> serviceToFields) {
        /*
         StarterType(Service1 service1, Service2 service2) {
         */
        Constructor.Builder ctr = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        serviceToFields.forEach((type, fieldName) -> {
            ctr.addParameter(service -> service
                    .type(type)
                    .name(fieldName));
            ctr.addContent("this.")
                    .addContent(fieldName)
                    .addContent(" = ")
                    .addContent(fieldName)
                    .addContentLine(";");
        });

        classModel.addConstructor(ctr);
    }

    private void addFields(ClassModel.Builder classModel, Map<TypeName, String> serviceToFields, List<Scheduled> allScheduled) {
        // private final ServiceType service_1;
        // private final Task task_1;
        serviceToFields.forEach((type, fieldName) -> {
            classModel.addField(service -> service
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(type)
                    .name(fieldName));
        });

        for (Scheduled scheduled : allScheduled) {
            classModel.addField(service -> service
                    .accessModifier(AccessModifier.PRIVATE)
                    .isVolatile(true)
                    .type(SchedulingTypes.TASK)
                    .name("task_" + scheduled.index()));
        }
    }

    private void collectServiceTypes(Map<TypeName, String> serviceToFields, List<Scheduled> allScheduled) {
        int counter = 0;
        for (Scheduled scheduled : allScheduled) {
            TypeName typeName = scheduled.serviceType();

            String fieldName = serviceToFields.get(typeName);
            if (fieldName == null) {
                fieldName = "service_" + counter++;
                serviceToFields.put(typeName, fieldName);
            }
        }
    }

    private String packageName(List<Scheduled> allScheduled) {
        String found = null;
        for (Scheduled s : allScheduled) {
            String packageName = s.serviceType().packageName();
            if (found == null) {
                found = packageName;
                continue;
            }
            if (found.length() > packageName.length()) {
                found = packageName;
            }
        }
        return found;
    }

    private void processCron(RegistryRoundContext roundContext,
                             List<Scheduled> allScheduled,
                             TypeInfo typeInfo,
                             TypedElementInfo element,
                             AtomicInteger scheduledCounter) {
        // type must be injectable
        checkTypeIsService(roundContext, typeInfo, CRON_ANNOTATION);
        // there can be a method argument, but it must be of correct type
        boolean hasInvocationArgument = checkAndHasArgument(typeInfo, element, CRON_INVOCATION);

        // read annotation values
        // read annotation values
        Annotation annotation = element.annotation(CRON_ANNOTATION);
        String expression = annotation.stringValue().orElse("* * * * * ?"); // required
        boolean concurrent = annotation.booleanValue("concurrent").orElse(true);

        // add for processing
        allScheduled.add(new Cron(typeInfo.typeName(),
                                  element.elementName(),
                                  hasInvocationArgument,
                                  scheduledCounter.getAndIncrement(),
                                  expression,
                                  concurrent));
    }

    private void processFixedRate(RegistryRoundContext roundContext,
                                  List<Scheduled> allScheduled,
                                  TypeInfo typeInfo,
                                  TypedElementInfo element,
                                  AtomicInteger scheduledCounter) {
        // type must be injectable
        checkTypeIsService(roundContext, typeInfo, FIXED_RATE_ANNOTATION);
        // there can be a method argument, but it must be of correct type
        boolean hasInvocationArgument = checkAndHasArgument(typeInfo, element, FIXED_RATE_INVOCATION);

        // read annotation values
        Annotation annotation = element.annotation(FIXED_RATE_ANNOTATION);
        String rate = annotation.stringValue().orElse("PT10S"); // required
        String delayBy = annotation.stringValue("delayBy").orElse("PT0S");
        String delayType = annotation.stringValue("delayType").orElse("SINCE_PREVIOUS_START");

        CodegenValidator.validateDuration(typeInfo.typeName(), element, FIXED_RATE_ANNOTATION, "interval", rate);
        CodegenValidator.validateDuration(typeInfo.typeName(), element, FIXED_RATE_ANNOTATION, "delayBy", delayBy);

        // add for processing
        allScheduled.add(new FixedRate(typeInfo.typeName(),
                                       element.elementName(),
                                       hasInvocationArgument,
                                       scheduledCounter.getAndIncrement(),
                                       rate,
                                       delayBy,
                                       delayType));
    }

    private boolean checkAndHasArgument(TypeInfo typeInfo, TypedElementInfo element, TypeName fixedRateInvocation) {
        List<TypedElementInfo> typedElementInfos = element.parameterArguments();
        if (typedElementInfos.size() == 1
                && typedElementInfos.getFirst().typeName().equals(FIXED_RATE_INVOCATION)) {
            return true;
        } else if (typedElementInfos.isEmpty()) {
            return false;
        } else {
            throw new CodegenException("Scheduling methods may have zero or one arguments. The argument for @FixedRate "
                                               + "must be of type " + FIXED_RATE_INVOCATION.fqName() + ".",
                                       element.originatingElementValue());
        }
    }

    private void checkTypeIsService(RegistryRoundContext roundContext, TypeInfo typeInfo, TypeName annotationType) {
        Optional<ClassModel.Builder> descriptor = roundContext.generatedType(ctx.descriptorType(typeInfo.typeName()));
        if (descriptor.isEmpty()) {
            throw new CodegenException("Type annotated with @" + annotationType.classNameWithEnclosingNames()
                                               + " is not a service itself. It must be annotated with "
                                               + SERVICE_ANNOTATION_SINGLETON.classNameWithEnclosingNames() + ".",
                                       typeInfo.originatingElementValue());
        }
    }

    private TypeInfo enclosingTypeInfo(Map<TypeName, TypeInfo> types, TypedElementInfo element) {
        Optional<TypeName> enclosingType = element.enclosingType();
        if (enclosingType.isEmpty()) {
            throw new CodegenException("Element " + element + " does not have an enclosing type",
                                       element.originatingElementValue());
        }
        TypeName typeName = enclosingType.get();
        TypeInfo typeInfo = types.get(typeName);
        if (typeInfo == null) {
            throw new CodegenException("Method enclosing type is not part of this processing round. Method: "
                                               + typeName.fqName() + "." + element.elementName(),
                                       element.originatingElementValue());
        }
        return typeInfo;
    }

    private interface Scheduled {
        boolean hasParameter();

        TypeName serviceType();

        String methodName();

        int index();

        void createScheduledContent(ContentBuilder<?> content);
    }

    private record Cron(TypeName serviceType,
                        String methodName,
                        boolean hasParameter,
                        int index,
                        String expression,
                        boolean concurrent)
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

    private record FixedRate(TypeName serviceType,
                             String methodName,
                             boolean hasParameter,
                             int index,
                             String rate,
                             String delayBy,
                             String delayType) implements Scheduled {
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
                content.addContent("delayBy(")
                        .addContent(Duration.class)
                        .addContent(".parse(\"")
                        .addContent(delayBy)
                        .addContentLine("\"))");
            }
            if (!"SINCE_PREVIOUS_START".equals(delayType)) {
                content.addContent("delayType(")
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
