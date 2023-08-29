/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import io.helidon.common.processor.TypeInfoFactory;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.config.metadata.processor.ConfiguredType.ConfiguredProperty;
import io.helidon.config.metadata.processor.ConfiguredType.ProducerMethod;

import static io.helidon.config.metadata.processor.UsedTypes.BLUEPRINT;
import static io.helidon.config.metadata.processor.UsedTypes.CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;

/*
 * This class is separated so javac correctly reports possible errors.
 */
class ConfigMetadataHandler {
    /*
     * Configuration metadata file location.
     */
    private static final String META_FILE = "META-INF/helidon/config-metadata.json";
    private static final Pattern JAVADOC_CODE = Pattern.compile("\\{@code (.*?)}");
    private static final Pattern JAVADOC_LINK = Pattern.compile("\\{@link (.*?)}");
    private static final String UNCONFIGURED_OPTION = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    // Newly created options as part of this processor run - these will be stored to META_FILE
    // map of type name to its configured type
    private final Map<TypeName, ConfiguredType> newOptions = new HashMap<>();
    // map of module name to list of classes that belong to it
    private final Map<String, List<TypeName>> moduleTypes = new HashMap<>();
    private final Set<Element> classesToHandle = new LinkedHashSet<>();
    /*
     * Compiler utilities for annotation processing
     */
    private Elements elementUtils;
    private Messager messager;
    private TypeElement configuredElement;
    private Filer filer;
    private Types typeUtils;

    /*
     * Type mirrors we use for comparison
     */
    private TypeMirror builderType;
    private TypeMirror factoryType;
    private TypeMirror configType;
    private TypeMirror commonConfigType;
    private TypeMirror erasedOptionalType;
    private TypeMirror erasedListType;
    private TypeMirror erasedIterableType;
    private TypeMirror erasedSetType;
    private TypeMirror erasedMapType;
    private ProcessingEnvironment processingEnv;

    /**
     * Public constructor required for service loader.
     */
    ConfigMetadataHandler() {
    }

    static <T> T findValue(AnnotationMirror annotationMirror,
                           String name,
                           Class<T> type,
                           T defaultValue) {
        return findValue(annotationMirror, name)
                .map(type::cast)
                .orElse(defaultValue);
    }

    static <T> Optional<T> findValue(AnnotationMirror annotationMirror,
                                     String name,
                                     Class<T> type) {
        return findValue(annotationMirror, name)
                .map(type::cast);
    }

    static Optional<Object> findValue(AnnotationMirror mirror, String name) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return Optional.of(entry.getValue().getValue());
            }
        }
        return Optional.empty();
    }

    static List<AllowedValue> allowedValues(Elements elementUtils, TypeElement typeElement) {
        if (typeElement != null && typeElement.getKind() == ElementKind.ENUM) {
            return typeElement.getEnclosedElements()
                    .stream()
                    .filter(element -> element.getKind().equals(ElementKind.ENUM_CONSTANT))
                    .map(element -> new AllowedValue(element.toString(), javadoc(elementUtils.getDocComment(element))))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    synchronized void init(ProcessingEnvironment processingEnv) {
        // get compiler utilities
        processingEnv = processingEnv;
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();

        // get the types
        configuredElement = elementUtils.getTypeElement(CONFIGURED_CLASS);
        TypeElement builderTypeElement = elementUtils.getTypeElement("io.helidon.common.Builder");
        if (builderTypeElement != null) {
            builderType = builderTypeElement.asType();
        }
        TypeElement factoryTypeElement = elementUtils.getTypeElement("io.helidon.builder.api.Prototype.Factory");
        if (factoryTypeElement != null) {
            factoryType = factoryTypeElement.asType();
        }
        TypeElement configTypeElement = elementUtils.getTypeElement("io.helidon.config.Config");
        if (configTypeElement != null) {
            configType = configTypeElement.asType();
        }
        TypeElement commonConfigTypeElement = elementUtils.getTypeElement("io.helidon.common.config.Config");
        if (commonConfigTypeElement != null) {
            commonConfigType = commonConfigTypeElement.asType();
        }

        erasedOptionalType = typeUtils.erasure(elementUtils.getTypeElement(Optional.class.getName()).asType());
        erasedListType = typeUtils.erasure(elementUtils.getTypeElement(List.class.getName()).asType());
        erasedSetType = typeUtils.erasure(elementUtils.getTypeElement(Set.class.getName()).asType());
        erasedIterableType = typeUtils.erasure(elementUtils.getTypeElement(Iterable.class.getName()).asType());
        erasedMapType = typeUtils.erasure(elementUtils.getTypeElement(Map.class.getName()).asType());
    }

    boolean process(RoundEnvironment roundEnv) {
        try {
            return doProcess(roundEnv);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process config metadata annotation processor. "
                    + toMessage(e));
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    List<ConfiguredOptionData> findConfiguredOptionAnnotations(ExecutableElement element) {
        Optional<AnnotationMirror> annotation = findAnnotation(element, CONFIGURED_OPTIONS_CLASS);

        if (annotation.isPresent()) {
            return findValue(annotation.get(), "value", List.class, List.of())
                    .stream()
                    .map(it -> ConfiguredOptionData.create(elementUtils, typeUtils, (AnnotationMirror) it))
                    .toList();
        }

        annotation = findAnnotation(element, CONFIGURED_OPTION_CLASS);

        return annotation.map(List::of)
                .orElseGet(List::of)
                .stream()
                .map(it -> ConfiguredOptionData.create(elementUtils, typeUtils, it))
                .toList();
    }

    /*
    Method name is camel case (such as maxInitialLineLength)
    result is dash separated and lower cased (such as max-initial-line-length).
    Note that this same method was created in ConfigUtils in common-config, but since this
    module should not have any dependencies in it a copy was left here as well.
     */
    String toConfigKey(String methodName) {
        StringBuilder result = new StringBuilder(methodName.length() + 5);

        char[] chars = methodName.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.length() == 0) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }

    private static String javadoc(String docComment) {
        if (null == docComment) {
            return "";
        }

        String javadoc = docComment;
        int index = javadoc.indexOf("@param");
        if (index > -1) {
            javadoc = docComment.substring(0, index);
        }
        // replace all {@code xxx} with 'xxx'
        javadoc = JAVADOC_CODE.matcher(javadoc).replaceAll(it -> '`' + it.group(1) + '`');
        // replace all {@link ...} with just the name
        javadoc = JAVADOC_LINK.matcher(javadoc).replaceAll(it -> it.group(1));

        return javadoc.trim();
    }

    private boolean doProcess(RoundEnvironment roundEnv) {
        // we need to collect all types for processing
        classesToHandle.addAll(roundEnv.getElementsAnnotatedWith(configuredElement));
        if (roundEnv.processingOver()) {
            for (Element aClass : classesToHandle) {
                processClass(aClass);
            }

            storeMetadata();
        }

        return false;
    }

    /*
     * This is a class annotated with @Configured
     */
    private void processClass(Element aClass) {
        if (aClass instanceof TypeElement typeElement) {
            Optional<TypeInfo> typeInfo = TypeInfoFactory.create(processingEnv,
                                                                 typeElement);
            if (typeInfo.isEmpty()) {
                // this type cannot be analyzed
                return;
            }

            MetadataHandler handler;

            TypeInfo configuredType = typeInfo.get();
            if (configuredType.hasAnnotation(META_CONFIGURED)) {
                if (configuredType.hasAnnotation(BLUEPRINT)) {
                    // old style - if using config meta annotation on class, expecting config meta annotations on options
                    handler = new MetadataHandlerBlueprintConfig(configuredType);
                } else {
                    // this is not a blueprint, fallback to configured types (before builder API and processor)
                    handler = new MetadataHandlerBlueprint(configuredType);
                }
            } else if (configuredType.hasAnnotation(CONFIGURED)) {
                // only new style of annotations (we expect that if class annotation is changed to Prototype.Configured)
                // all other annotations are migrated as well
                handler = new MetadataHandlerConfig(configuredType);
            } else {
                throw new IllegalArgumentException("Requested to process type: " + aClass + ", but it does not have required "
                                                           + "annotation");
            }

            handler.handle();

            TypeName targetType = handler.targetType();
            ConfiguredType configured = handler.configuredType();

            newOptions.put(targetType, configured);
            moduleTypes.computeIfAbsent(handler.moduleName(), it -> new ArrayList<>()).add(targetType);
        }
    }

    private void processBlueprintMetaAnnotations(TypeInfo typeInfo, ConfiguredAnnotation configured) {
        String blueprintType = toDocumentedName(interfaceElement);
        String targetType = typeForBlueprint(interfaceElement, blueprintType);
        ConfiguredType type = new ConfiguredType(configured,
                                                 blueprintType,
                                                 targetType,
                                                 true);

        newOptions.put(targetType, type);

        String module = elementUtils.getModuleOf(interfaceElement).toString();
        moduleTypes.computeIfAbsent(module, it -> new ArrayList<>()).add(targetType);

        addInterfaces(type, interfaceElement);

        processBlueprint(interfaceElement, type, blueprintType, targetType);
    }

    private String typeForBlueprint(TypeElement interfaceElement, String configObjectType) {
        // if the type implements `Factory<X>`, we want to return X, otherwise "pure" config object
        List<? extends TypeMirror> interfaces = interfaceElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            if (anInterface instanceof DeclaredType type) {
                if (typeUtils.isSameType(typeUtils.erasure(factoryType), typeUtils.erasure(type))) {
                    TypeMirror builtType = type.getTypeArguments().get(0);
                    return typeUtils.erasure(builtType).toString();
                }
            }
        }
        return configObjectType;
    }

    private BuilderTypeInfo findBuilder(TypeElement classElement) {
        List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            if (anInterface instanceof DeclaredType) {
                DeclaredType type = (DeclaredType) anInterface;
                if (typeUtils.isSameType(typeUtils.erasure(builderType), typeUtils.erasure(type))) {
                    TypeMirror builtType = type.getTypeArguments().get(1);
                    return new BuilderTypeInfo(typeUtils.erasure(builtType).toString());
                }
            }
        }
        BuilderTypeInfo found;
        // did not find it, let's try super interfaces of interfaces
        for (TypeMirror anInterface : interfaces) {
            if (anInterface instanceof DeclaredType) {
                DeclaredType type = (DeclaredType) anInterface;
                Element element = type.asElement();
                if (element instanceof TypeElement) {
                    found = findBuilder((TypeElement) element);
                    if (found.isBuilder) {
                        return found;
                    }
                }
            }
        }
        TypeMirror superMirror = classElement.getSuperclass();
        String buildTarget = findBuildMethodTarget(classElement);
        if (superMirror.getKind() != TypeKind.NONE) {
            TypeElement superclass = (TypeElement) typeUtils.asElement(typeUtils.erasure(superMirror));
            found = findBuilder(superclass);
            if (found.isBuilder) {
                if (buildTarget == null) {
                    return found;
                } else {
                    return new BuilderTypeInfo(buildTarget);
                }
            }
        }
        return new BuilderTypeInfo();
    }

    private void addInterfaces(ConfiguredType type, TypeInfo typeInfo) {
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            if (interfaceInfo.hasAnnotation(META_CONFIGURED)) {
                type.addInherited(interfaceInfo.typeName());
            } else {
                addSuperClasses(type, interfaceInfo);
            }
        }
    }

    private void addInterfaces(ConfiguredType type, TypeElement classElement) {
        List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            TypeElement interfaceElement = (TypeElement) typeUtils.asElement(typeUtils.erasure(anInterface));
            if (findAnnotation(interfaceElement, CONFIGURED_CLASS).isPresent()) {
                // if superclass is annotated as well, it will be referenced from the interface
                type.addInherited(interfaceElement.toString());
            } else {
                // check if there is a superclass annotated, add that
                addSuperClasses(type, interfaceElement);
            }
        }
    }

    private void addSuperClasses(ConfiguredType type, TypeInfo typeInfo) {
        Optional<TypeInfo> foundSuperType = typeInfo.superTypeInfo();
        if (foundSuperType.isEmpty()) {
            return;
        }
        TypeInfo superClass = foundSuperType.get();

        while (true) {
            if (superClass.hasAnnotation(META_CONFIGURED)) {
                // we only care about the first one. This one should reference its superclass/interfaces
                // if they are configured as well
                type.addInherited(superClass.typeName());
                return;
            }

            foundSuperType = superClass.superTypeInfo();
            if (foundSuperType.isEmpty()) {
                return;
            }
            superClass = foundSuperType.get();
        }
    }

    private void addSuperClasses(ConfiguredType type, TypeElement classElement) {
        TypeMirror superMirror = classElement.getSuperclass();
        if (superMirror.getKind() == TypeKind.NONE) {
            return;
        }
        TypeElement superclass = (TypeElement) typeUtils.asElement(typeUtils.erasure(superMirror));
        while (true) {
            if (findAnnotation(superclass, CONFIGURED_CLASS).isPresent()) {
                type.addInherited(superclass.toString());
                // we only care about the first one. This one should reference its superclass/interfaces
                // if they are configured as well
                return;
            }
            superMirror = superclass.getSuperclass();
            if (superMirror.getKind() == TypeKind.NONE) {
                return;
            }
            superclass = (TypeElement) typeUtils.asElement(superMirror);
        }
    }

    private void processBlueprint(TypeElement interfaceElement,
                                  ConfiguredType type,
                                  String configObjectType,
                                  String targetType) {

        type.addProducer(new ProducerMethod(true, configObjectType, "create", new String[] {"io.helidon.config.Config"}));
        type.addProducer(new ProducerMethod(true, configObjectType, "builder", new String[0]));

        if (!targetType.equals(configObjectType)) {
            // target type may not have the method (if it is for example PrivateKey)
            type.addProducer(new ProducerMethod(false, configObjectType, "fromConfig", new String[0]));
        }

        // and now process all blueprint methods - must be non default, non static
        // methods on this interface

        elementUtils.getAllMembers(interfaceElement)
                .stream()
                .filter(this::isMethod)
                .map(this::toExecutableElement)
                .filter(it -> isMine(interfaceElement, it))
                .filter(Predicate.not(this::isStatic))
                .filter(Predicate.not(this::isDefault))
                .forEach(it -> processBlueprintMethod(it, type, configObjectType));
    }

    private boolean isVoid(ExecutableElement element) {
        return element.getReturnType().getKind() == TypeKind.VOID;
    }

    private boolean isDefault(Element element) {
        return element.getModifiers().contains(Modifier.DEFAULT);
    }

    private boolean isPublic(Element element) {
        return element.getModifiers().contains(Modifier.PUBLIC);
    }

    private boolean isStatic(Element element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    private ExecutableElement toExecutableElement(Element element) {
        return (ExecutableElement) element;
    }

    private boolean isMethod(Element element) {
        return element.getKind() == ElementKind.METHOD;
    }

    private void processBuilderType(TypeElement builderElement,
                                    ConfiguredType type,
                                    String className,
                                    String targetClass) {

        type.addProducer(new ProducerMethod(false, className, "build", new String[0]));

        // find create(Config) method on the class
        if (hasCreate(elementUtils.getTypeElement(targetClass))) {
            type.addProducer(new ProducerMethod(true, targetClass, "create",
                                                new String[] {"io.helidon.config.Config"}));
        }

        elementUtils.getAllMembers(builderElement)
                .stream()
                .filter(this::isMethod)
                .map(this::toExecutableElement)
                // the method is declared by this builder (if not, it is from super class or interface -> already handled)
                .filter(it -> isMine(builderElement, it))
                // public
                .filter(this::isPublic)
                // not static
                .filter(Predicate.not(this::isStatic))
                // return the same type (e.g. Builder)
                .filter(it -> isBuilderMethod(builderElement, it))
                .forEach(it -> processBuilderMethod(it, type, className));
    }

    private boolean isMine(TypeElement type, ExecutableElement method) {
        Element enclosingElement = method.getEnclosingElement();
        return type.equals(enclosingElement);
    }

    private boolean isBuilderMethod(TypeElement builderElement, ExecutableElement it) {
        TypeMirror builderType = builderElement.asType();
        TypeMirror methodReturnType = it.getReturnType();
        if (typeUtils.isSameType(builderType, methodReturnType)) {
            return true;
        }
        return findAnnotation(it, CONFIGURED_OPTION_CLASS).isPresent();
    }

    private void processTargetType(TypeElement typeElement,
                                   ConfiguredType type,
                                   String className,
                                   boolean standalone) {
        // go through all methods, find all create methods and create appropriate configured producers for them
        // if there is a builder, add the builder producer as well

        List<ExecutableElement> methods = elementUtils.getAllMembers(typeElement)
                .stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                // public
                .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                // static
                .filter(it -> it.getModifiers().contains(Modifier.STATIC))
                .collect(Collectors.toList());

        // either this is a target class (such as an interface with create method)
        // or this is an interface/abstract class inherited by builders

        boolean isTargetType = false;
        List<ExecutableElement> validMethods = new LinkedList<>();
        ExecutableElement configCreator = null;

        // now we have just public static methods, let's look for create/builder
        for (ExecutableElement method : methods) {
            Name simpleName = method.getSimpleName();

            if (simpleName.contentEquals("create")) {
                if (typeUtils.isSameType(typeElement.asType(), method.getReturnType())) {
                    validMethods.add(method);
                    List<? extends VariableElement> parameters = method.getParameters();
                    if (parameters.size() == 1) {
                        if (isConfigType(parameters.get(0).asType())) {
                            configCreator = method;
                        }
                    }
                    isTargetType = true;
                }
            } else if (simpleName.contentEquals("builder")) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Type " + className + " is marked with @Configured"
                        + ", yet it has a static builder() method. Please mark the builder instead of this class.");
            }
        }

        if (isTargetType) {
            if (configCreator != null) {
                type.addProducer(new ProducerMethod(true,
                                                    className,
                                                    configCreator.getSimpleName().toString(),
                                                    methodParams(configCreator)));
            }
            // now let's find all methods with @ConfiguredOption
            for (ExecutableElement validMethod : validMethods) {
                List<ConfiguredOptionData> options = findConfiguredOptionAnnotations(validMethod);

                if (options.isEmpty()) {
                    continue;
                }

                for (ConfiguredOptionData data : options) {
                    if ((data.name == null || data.name.isBlank()) && !data.merge) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                              "ConfiguredOption on " + typeElement + "."
                                                      + validMethod
                                                      + " does not have value defined. It is mandatory on non-builder methods",
                                              typeElement);
                        return;
                    }

                    if (data.description == null || data.description.isBlank()) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                              "ConfiguredOption on " + typeElement + "." + validMethod
                                                      + " does not have description defined. It is mandatory on non-builder "
                                                      + "methods",
                                              typeElement);
                        return;
                    }

                    if (data.type == null) {
                        // this is the default value
                        data.type = "java.lang.String";
                    }

                    ConfiguredProperty prop = new ConfiguredProperty(null,
                                                                     data.name,
                                                                     data.description,
                                                                     data.defaultValue,
                                                                     data.type,
                                                                     data.experimental,
                                                                     !data.required,
                                                                     data.kind,
                                                                     data.provider,
                                                                     data.deprecated,
                                                                     data.merge,
                                                                     data.allowedValues);
                    type.addProperty(prop);
                }
            }
        } else {
            // this must be a class/interface used by other classes to extend, so we care about all builder style
            // methods
            if (standalone) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                                      "Type " + className + " is marked as standalone configuration unit, yet it does have "
                                              + "neither a builder method, nor a create method");
                return;
            }
            elementUtils.getAllMembers(typeElement)
                    .stream()
                    .filter(it -> it.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(it -> isMine(typeElement, it))
                    // public
                    .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                    // not static
                    .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
                    .forEach(it -> processBuilderMethod(
                            it,
                            type,
                            className));
        }
    }

    private boolean isConfigType(TypeMirror typeMirror) {
        if (configType != null && typeUtils.isSameType(typeMirror, configType)) {
            return true;
        } else if (commonConfigType != null && typeUtils.isSameType(typeMirror, commonConfigType)) {
            return true;
        }
        return false;
    }

    private String findBuildMethodTarget(TypeElement typeElement) {
        return elementUtils.getAllMembers(typeElement)
                .stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                // public
                .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                // static
                .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
                .filter(it -> it.getSimpleName().contentEquals("build"))
                .filter(it -> it.getParameters().isEmpty())
                .filter(it -> it.getReturnType().getKind() != TypeKind.VOID)
                .findFirst()
                .map(it -> it.getReturnType().toString())
                .orElse(null);
    }

    private boolean hasCreate(TypeElement typeElement) {
        return elementUtils.getAllMembers(typeElement)
                .stream()
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                // public
                .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                // static
                .filter(it -> it.getModifiers().contains(Modifier.STATIC))
                // Me (must return its own type)
                .filter(it -> typeUtils.isSameType(typeElement.asType(), it.getReturnType()))
                // create
                .filter(it -> it.getSimpleName().contentEquals("create"))
                // (Config)
                .anyMatch(it -> {
                    List<? extends VariableElement> parameters = it.getParameters();
                    if (parameters.size() == 1) {
                        VariableElement parameter = parameters.iterator().next();
                        // public static Me create(...)
                        return isConfigType(parameter.asType());
                    }
                    return false;
                });
    }

    private void processBlueprintMethod(ExecutableElement element, ConfiguredType configuredType, String configObjectType) {
        List<ConfiguredOptionData> options = findConfiguredOptionAnnotations(element);

        for (ConfiguredOptionData data : options) {
            String name = key(data.name, element);
            String description = description(data.description, element);
            String defaultValue = defaultValue(data.defaultValue);
            boolean experimental = data.experimental;
            OptionType type = typeForBlueprint(data, element);
            boolean optional = defaultValue != null || !data.required;
            boolean deprecated = data.deprecated;
            List<AllowedValue> allowedValues = allowedValues(data, type.elementType);

            String[] paramTypes = new String[] {type.elementType};

            ProducerMethod builderMethod = new ProducerMethod(false,
                                                              configObjectType + ".Builder",
                                                              element.getSimpleName().toString(),
                                                              paramTypes);

            ConfiguredProperty property = new ConfiguredProperty(builderMethod.toString(),
                                                                 name,
                                                                 description,
                                                                 defaultValue,
                                                                 type.elementType,
                                                                 experimental,
                                                                 optional,
                                                                 type.kind,
                                                                 data.provider,
                                                                 deprecated,
                                                                 data.merge,
                                                                 allowedValues);
            configuredType.addProperty(property);
        }
    }

    private void processBuilderMethod(ExecutableElement element,
                                      ConfiguredType configuredType,
                                      String className) {

        List<ConfiguredOptionData> options = findConfiguredOptionAnnotations(element);
        if (options.isEmpty()) {
            return;
        }

        for (ConfiguredOptionData data : options) {
            String name = key(data.name, element);
            String description = description(data.description, element);
            String defaultValue = defaultValue(data.defaultValue);
            boolean experimental = data.experimental;
            OptionType type = type(data, element);
            boolean optional = defaultValue != null || !data.required;
            boolean deprecated = data.deprecated;
            List<AllowedValue> allowedValues = allowedValues(data, type.elementType);

            String[] paramTypes = methodParams(element);

            ProducerMethod builderMethod = new ProducerMethod(false,
                                                              className,
                                                              element.getSimpleName().toString(),
                                                              paramTypes);

            ConfiguredProperty property = new ConfiguredProperty(builderMethod.toString(),
                                                                 name,
                                                                 description,
                                                                 defaultValue,
                                                                 type.elementType,
                                                                 experimental,
                                                                 optional,
                                                                 type.kind,
                                                                 data.provider,
                                                                 deprecated,
                                                                 data.merge,
                                                                 allowedValues);
            configuredType.addProperty(property);
        }
    }

    private String[] methodParams(ExecutableElement element) {
        return element.getParameters()
                .stream()
                .map(it -> it.asType().toString())
                .toArray(String[]::new);
    }

    private String description(String description, Element element) {
        if (description == null || description.isBlank()) {
            return javadoc(elementUtils.getDocComment(element));
        }
        return description;
    }

    private String defaultValue(String defaultValue) {
        return UNCONFIGURED_OPTION.equals(defaultValue) ? null : defaultValue;
    }

    private OptionType typeForBlueprint(ConfiguredOptionData annotation, ExecutableElement element) {
        if (annotation.type == null || annotation.type.equals(CONFIGURED_OPTION_CLASS)) {
            return typeForBlueprintFromSignature(element, annotation);
        } else {
            // use the one defined on annotation
            return new OptionType(annotation.type, annotation.kind);
        }
    }

    private OptionType typeForBlueprintFromSignature(ExecutableElement element, ConfiguredOptionData annotation) {
        // guess from method

        if (!element.getParameters().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method " + element + " is annotated with @Configured, "
                    + "yet it has a parameter. Interface methods must not have parameters.", element);
            throw new IllegalStateException("Could not determine property type");
        }

        TypeMirror paramType = element.getReturnType();
        if (paramType.getKind() == TypeKind.VOID) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Method " + element + " is annotated with @Configured, "
                    + "yet it is void. Interface methods must return the property type.", element);
            throw new IllegalStateException("Could not determine property type");
        }

        TypeMirror erasedType = typeUtils.erasure(paramType);

        if (typeUtils.isSameType(erasedOptionalType, erasedType)) {
            DeclaredType type = (DeclaredType) paramType;
            TypeMirror genericType = type.getTypeArguments().get(0);
            return new OptionType(genericType.toString(), "VALUE");
        }

        if (typeUtils.isSameType(erasedListType, erasedType) || typeUtils.isSameType(erasedType, erasedSetType)
                || typeUtils.isSameType(erasedType, erasedIterableType)) {
            DeclaredType type = (DeclaredType) paramType;
            TypeMirror genericType = type.getTypeArguments().get(0);
            return new OptionType(genericType.toString(), "LIST");
        }

        if (typeUtils.isSameType(erasedMapType, erasedType)) {
            DeclaredType type = (DeclaredType) paramType;
            TypeMirror genericType = type.getTypeArguments().get(1);
            return new OptionType(genericType.toString(), "MAP");
        }

        String typeName;
        if (paramType instanceof PrimitiveType) {
            typeName = typeUtils.boxedClass((PrimitiveType) paramType).toString();
        } else {
            typeName = paramType.toString();
        }
        return new OptionType(typeName, annotation.kind);
    }

    private OptionType type(ConfiguredOptionData annotation, ExecutableElement element) {
        if (annotation.type == null || annotation.type.equals(CONFIGURED_OPTION_CLASS)) {
            // guess from method

            List<? extends VariableElement> parameters = element.getParameters();
            if (parameters.size() != 1) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Method " + element + " is annotated with @Configured, "
                        + "yet it does not have explicit type, or exactly one parameter", element);
                throw new IllegalStateException("Could not determine property type");
            } else {
                VariableElement parameter = parameters.iterator().next();
                TypeMirror paramType = parameter.asType();
                TypeMirror erasedType = typeUtils.erasure(paramType);

                if (typeUtils.isSameType(erasedType, erasedListType) || typeUtils.isSameType(erasedType, erasedSetType)
                        || typeUtils.isSameType(erasedType, erasedIterableType)) {
                    DeclaredType type = (DeclaredType) paramType;
                    TypeMirror genericType = type.getTypeArguments().get(0);
                    return new OptionType(genericType.toString(), "LIST");
                }

                if (typeUtils.isSameType(erasedType, erasedMapType)) {
                    DeclaredType type = (DeclaredType) paramType;
                    TypeMirror genericType = type.getTypeArguments().get(1);
                    return new OptionType(genericType.toString(), "MAP");
                }

                String typeName;
                if (paramType instanceof PrimitiveType) {
                    typeName = typeUtils.boxedClass((PrimitiveType) paramType).toString();
                } else {
                    typeName = paramType.toString();
                }
                return new OptionType(typeName, annotation.kind);
            }

        } else {
            // use the one defined on annotation
            return new OptionType(annotation.type, annotation.kind);
        }
    }

    private String key(String annotationValue, Element element) {
        if (annotationValue == null || annotationValue.isBlank()) {
            Name simpleName = element.getSimpleName();
            String methodName = simpleName.toString();
            return toConfigKey(methodName);
        }
        return annotationValue;
    }

    private void storeMetadata() {
        try (PrintWriter metaWriter = new PrintWriter(filer.createResource(StandardLocation.CLASS_OUTPUT,
                                                                           "",
                                                                           META_FILE)
                                                              .openWriter())) {

            /*
             The root of the json file is an array that contains module entries
             This is to allow merging of files - such as when we would want to create on-the-fly
             JSON for a project with only its dependencies.
             */
            JArray moduleArray = new JArray();

            for (var module : moduleTypes.entrySet()) {
                String moduleName = module.getKey();
                var types = module.getValue();
                JArray typeArray = new JArray();
                types.forEach(it -> newOptions.get(it).write(typeArray));
                moduleArray.add(new JObject()
                                        .add("module", moduleName)
                                        .add("types", typeArray));
            }

            moduleArray.write(metaWriter);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write configuration metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String toMessage(Exception e) {
        return e.getClass().getName() + ": " + e.getMessage();
    }

    private List<AllowedValue> allowedValues(ConfiguredOptionData annotation, String type) {
        if (type.equals(annotation.type) || !annotation.allowedValues.isEmpty()) {
            // this was already processed due to an explicit type defined in the annotation
            // or allowed values explicitly configured in annotation
            return annotation.allowedValues;
        }
        return allowedValues(elementUtils, elementUtils.getTypeElement(type));
    }

    private String toDocumentedName(TypeElement interfaceElement) {
        String className = interfaceElement.toString();

        return findAnnotation(interfaceElement,
                              "io.helidon.builder.api.Prototype.Blueprint").filter(it -> className.endsWith("Blueprint"))
                .map(it -> className.substring(0, className.length() - "Blueprint".length()))
                .orElse(className);
    }

    private static final class OptionType {
        private final String elementType;
        private final String kind;

        private OptionType(String elementType, String kind) {
            this.elementType = elementType;
            this.kind = kind;
        }
    }

    static final class AllowedValue {
        private String value;
        private String description;

        AllowedValue() {
        }

        AllowedValue(String value, String description) {
            this.value = value;
            this.description = description;
        }

        String value() {
            return value;
        }

        String description() {
            return description;
        }

        private static AllowedValue create(AnnotationMirror annotationMirror) {
            AllowedValue result = new AllowedValue();
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror.getElementValues();

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                Name key = entry.getKey().getSimpleName();
                Object value = entry.getValue().getValue();

                if (key.contentEquals("value")) {
                    result.value = (String) value;
                } else if (key.contentEquals("description")) {
                    result.description = (String) value;
                }
            }

            return result;
        }
    }

    private static final class ConfiguredOptionData {
        private final List<AllowedValue> allowedValues = new LinkedList<>();

        private String name;
        private String type;
        private String description;
        private boolean required;
        private String defaultValue;
        private boolean experimental;
        private boolean provider;
        private boolean deprecated;
        private boolean merge;
        private String kind = "VALUE";

        @SuppressWarnings("unchecked")
        static ConfiguredOptionData create(Elements elementUtils, Types typeUtils, AnnotationMirror configuredMirror) {
            ConfiguredOptionData result = new ConfiguredOptionData();

            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = configuredMirror.getElementValues();

            TypeElement enumType = null;

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                Name key = entry.getKey().getSimpleName();
                Object value = entry.getValue().getValue();

                if (key.contentEquals("key")) {
                    result.name = (String) value;
                } else if (key.contentEquals("description")) {
                    result.description = (String) value;
                } else if (key.contentEquals("value")) {
                    result.defaultValue = (String) value;
                } else if (key.contentEquals("experimental")) {
                    result.experimental = (Boolean) value;
                } else if (key.contentEquals("required")) {
                    result.required = (Boolean) value;
                } else if (key.contentEquals("mergeWithParent")) {
                    result.merge = (Boolean) value;
                } else if (key.contentEquals("type")) {
                    TypeMirror typeMirror = (TypeMirror) value;
                    Element element = typeUtils.asElement(typeMirror);
                    if (element.getKind() == ElementKind.ENUM) {
                        enumType = (TypeElement) element;
                    }

                    result.type = value.toString();
                } else if (key.contentEquals("kind")) {
                    result.kind = value.toString();
                } else if (key.contentEquals("provider")) {
                    result.provider = (Boolean) value;
                } else if (key.contentEquals("deprecated")) {
                    result.deprecated = (Boolean) value;
                } else if (key.contentEquals("allowedValues")) {
                    ((List<AnnotationMirror>) value).stream()
                            .map(AllowedValue::create)
                            .forEach(result.allowedValues::add);

                }
            }
            if (result.allowedValues.isEmpty() && (enumType != null)) {
                result.allowedValues.addAll(allowedValues(elementUtils, enumType));
            }

            return result;
        }
    }

    private static class BuilderTypeInfo {
        private final boolean isBuilder;
        private final String targetClass;

        BuilderTypeInfo() {
            this.isBuilder = false;
            this.targetClass = null;
        }

        BuilderTypeInfo(String targetClass) {
            this.isBuilder = true;
            this.targetClass = targetClass;
        }
    }
}
