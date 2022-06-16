/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import io.helidon.config.metadata.processor.ConfiguredType.ConfiguredProperty;
import io.helidon.config.metadata.processor.ConfiguredType.ProducerMethod;

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
    private static final String ANNOTATIONS_PACKAGE = "io.helidon.config.metadata.";
    private static final String UNCONFIGURED_OPTION = ANNOTATIONS_PACKAGE + "ConfiguredOption.UNCONFIGURED";
    private static final String CONFIGURED_CLASS = ANNOTATIONS_PACKAGE + "Configured";
    private static final String CONFIGURED_OPTION_CLASS = ANNOTATIONS_PACKAGE + "ConfiguredOption";
    private static final String CONFIGURED_OPTIONS_CLASS = ANNOTATIONS_PACKAGE + "ConfiguredOptions";

    // Newly created options as part of this processor run - these will be stored to META_FILE
    // map of fully qualified class name to its configured type
    private final Map<String, ConfiguredType> newOptions = new HashMap<>();
    // map of module name to list of classes that belong to it
    private final Map<String, List<String>> moduleTypes = new HashMap<>();

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
    private TypeMirror configType;
    private TypeMirror erasedListType;
    private TypeMirror erasedIterableType;
    private TypeMirror erasedSetType;
    private TypeMirror erasedMapType;

    /**
     * Public constructor required for service loader.
     */
    ConfigMetadataHandler() {
    }

    synchronized void init(ProcessingEnvironment processingEnv) {
        // get compiler utilities
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();

        // get the types
        configuredElement = elementUtils.getTypeElement(CONFIGURED_CLASS);
        builderType = elementUtils.getTypeElement("io.helidon.common.Builder").asType();
        configType = elementUtils.getTypeElement("io.helidon.config.Config").asType();
        erasedListType = typeUtils.erasure(elementUtils.getTypeElement(List.class.getName()).asType());
        erasedSetType = typeUtils.erasure(elementUtils.getTypeElement(Set.class.getName()).asType());
        erasedIterableType = typeUtils.erasure(elementUtils.getTypeElement(Iterable.class.getName()).asType());
        erasedMapType = typeUtils.erasure(elementUtils.getTypeElement(Map.class.getName()).asType());
    }

    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return doProcess(roundEnv);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process config metadata annotation processor. "
                    + toMessage(e));
            e.printStackTrace();
            return false;
        }
    }

    private boolean doProcess(RoundEnvironment roundEnv) {
        Set<? extends Element> classes = roundEnv.getElementsAnnotatedWith(configuredElement);
        for (Element aClass : classes) {
            processClass(aClass);
        }

        if (roundEnv.processingOver()) {
            storeMetadata();
        }

        return false;
    }

    /*
     * This is a class annotated with @Configured
     */
    private void processClass(Element aClass) {
        findAnnotation(aClass, CONFIGURED_CLASS)
                .ifPresent(it -> processConfiguredAnnotation(aClass, it));
    }

    private Optional<AnnotationMirror> findAnnotation(Element annotatedElement, String annotationType) {
        for (AnnotationMirror mirror : annotatedElement.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().asElement().toString().equals(annotationType)) {
                return Optional.of(mirror);
            }
        }
        return Optional.empty();
    }

    private void processConfiguredAnnotation(Element aClass, AnnotationMirror mirror) {
        boolean standalone = findValue(mirror, "root", Boolean.class, false);
        String keyPrefix = findValue(mirror, "prefix", String.class, "");
        String description = findValue(mirror, "description", String.class, "");
        boolean ignoreBuildMethod = findValue(mirror, "ignoreBuildMethod", Boolean.class, false);

        String className = aClass.toString();
        String targetClass = className;
        boolean isBuilder = false;

        TypeElement classElement = (TypeElement) aClass;

        if (!ignoreBuildMethod
                && typeUtils.isAssignable(typeUtils.erasure(aClass.asType()), typeUtils.erasure(builderType))) {
            // this is a builder, we need the target type
            BuilderTypeInfo foundBuilder = findBuilder(classElement);
            isBuilder = foundBuilder.isBuilder;
            if (isBuilder) {
                targetClass = foundBuilder.targetClass;
            }
        }

        /*
          now we know whether this is
          - a builder + known target class (result of builder() method)
          - a standalone class (probably with public static create(Config) method)
          - an interface/abstract class only used for inheritance
         */

        ConfiguredType type = new ConfiguredType(className,
                                                 targetClass,
                                                 standalone,
                                                 keyPrefix,
                                                 description,
                                                 toProvides(aClass));

        newOptions.put(targetClass, type);
        String module = elementUtils.getModuleOf(aClass).toString();
        moduleTypes.computeIfAbsent(module, it -> new LinkedList<>()).add(targetClass);

        /*
         we also need to know all superclasses / interfaces that are configurable to allow merging
         of these
         */
        addSuperClasses(type, classElement);
        addInterfaces(type, classElement);

        if (isBuilder) {
            // builder
            processBuilderType(classElement, type, className, targetClass);
        } else {
            // standalone class with create method(s), or interface/abstract class
            processTargetType(classElement, type, className, standalone);
        }
    }

    private <T> T findValue(AnnotationMirror annotationMirror,
                            String name,
                            Class<T> type,
                            T defaultValue) {
        return findValue(annotationMirror, name)
                .map(type::cast)
                .orElse(defaultValue);
    }

    private Optional<Object> findValue(AnnotationMirror mirror, String name) {
        for (var entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return Optional.of(entry.getValue().getValue());
            }
        }
        return Optional.empty();
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

    @SuppressWarnings("unchecked")
    private List<String> toProvides(Element aClass) {
        return findAnnotation(aClass, CONFIGURED_CLASS)
                .map(mirror -> (List<AnnotationValue>) findValue(mirror, "provides", List.class, List.of()))
                .map(values -> values.stream()
                        .map(AnnotationValue::getValue)
                        .map(Object::toString)
                        .collect(Collectors.toList()))
                .orElseGet(List::of);
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
                .filter(it -> it.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                // the method is declared by this builder (if not, it is from super class or interface -> already handled)
                .filter(it -> isMine(builderElement, it))
                // public
                .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                // not static
                .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
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
                    if (parameters.size() == 1 && typeUtils.isSameType(parameters.get(0).asType(), configType)) {
                        configCreator = method;
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
                List<AnnotationMirror> options = findConfiguredOptionAnnotations(validMethod);

                if (options.isEmpty()) {
                    continue;
                }

                for (AnnotationMirror option : options) {
                    ConfiguredOptionData data = ConfiguredOptionData.create(elementUtils, typeUtils, option);

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
                                              typeElement,
                                              option);
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
                        return typeUtils.isSameType(configType, parameter.asType());
                    }
                    return false;
                });
    }

    private void processBuilderMethod(ExecutableElement element,
                                      ConfiguredType configuredType,
                                      String className) {

        List<AnnotationMirror> options = findConfiguredOptionAnnotations(element);
        if (options.isEmpty()) {
            return;
        }

        for (AnnotationMirror option : options) {
            ConfiguredOptionData data = ConfiguredOptionData.create(elementUtils, typeUtils, option);

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

    @SuppressWarnings("unchecked")
    List<AnnotationMirror> findConfiguredOptionAnnotations(ExecutableElement element) {
        Optional<AnnotationMirror> annotation = findAnnotation(element, CONFIGURED_OPTIONS_CLASS);

        if (annotation.isPresent()) {
            return findValue(annotation.get(), "value", List.class, List.of());
        }

        annotation = findAnnotation(element, CONFIGURED_OPTION_CLASS);

        return annotation.map(List::of)
                .orElseGet(List::of);
    }

    /*
    Method name is camel case (such as maxInitialLineLength)
    result is dash separated and lower cased (such as max-initial-line-length)
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

    private List<AllowedValue> allowedValues(ConfiguredOptionData annotation, String type) {
        if (type.equals(annotation.type) || !annotation.allowedValues.isEmpty()) {
            // this was already processed due to an explicit type defined in the annotation
            // or allowed values explicitly configured in annotation
            return annotation.allowedValues;
        }
        return allowedValues(elementUtils, elementUtils.getTypeElement(type));
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

        String value() {
            return value;
        }

        String description() {
            return description;
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
