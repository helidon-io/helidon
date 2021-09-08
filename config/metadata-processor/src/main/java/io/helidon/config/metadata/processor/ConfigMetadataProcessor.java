/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonWriter;
import javax.lang.model.SourceVersion;
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

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredOptions;
import io.helidon.config.metadata.processor.ConfiguredType.ConfiguredProperty;
import io.helidon.config.metadata.processor.ConfiguredType.ProducerMethod;

import static io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED;

/**
 * Annotation processor.
 *
 * TODO:
 *   - XML Schema?
 */
public class ConfigMetadataProcessor extends AbstractProcessor {
    /**
     * Configuration metadata file location.
     */
    private static final String META_FILE = "META-INF/helidon/config-metadata.json";
    private static final Pattern JAVADOC_CODE = Pattern.compile("\\{@code (.*?)}");
    private static final Pattern JAVADOC_LINK = Pattern.compile("\\{@link (.*?)}");

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
    private TypeElement configuredType;
    private Filer filer;
    private Types typeUtils;

    /*
     * Type mirrors we use for comparison
     */
    private TypeMirror builderType;
    private TypeMirror configType;
    private TypeMirror erasedListType;
    private TypeMirror erasedSetType;
    private TypeMirror erasedMapType;

    /**
     * Public constructor required for service loader.
     */
    public ConfigMetadataProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Stream.of(Configured.class, ConfiguredOptions.class, ConfiguredOption.class)
                .map(Class::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        // get compiler utilities
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();

        // get the types
        configuredType = elementUtils.getTypeElement(Configured.class.getName());
        builderType = elementUtils.getTypeElement("io.helidon.common.Builder").asType();
        configType = elementUtils.getTypeElement("io.helidon.config.Config").asType();
        erasedListType = typeUtils.erasure(elementUtils.getTypeElement(List.class.getName()).asType());
        erasedSetType = typeUtils.erasure(elementUtils.getTypeElement(Set.class.getName()).asType());
        erasedMapType = typeUtils.erasure(elementUtils.getTypeElement(Map.class.getName()).asType());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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
        Set<? extends Element> classes = roundEnv.getElementsAnnotatedWith(configuredType);
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
        Configured configured = aClass.getAnnotation(Configured.class);
        boolean standalone = configured.root();
        String keyPrefix = configured.prefix();
        String description = configured.description();

        String className = aClass.toString();
        String targetClass = className;
        boolean isBuilder = false;

        TypeElement classElement = (TypeElement) aClass;
        if (!configured.ignoreBuildMethod()
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

        ConfiguredType type = new ConfiguredType(targetClass,
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

    private BuilderTypeInfo findBuilder(TypeElement classElement) {
        List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            if (anInterface instanceof DeclaredType) {
                DeclaredType type = (DeclaredType) anInterface;
                if (typeUtils.isSameType(typeUtils.erasure(builderType), typeUtils.erasure(type))) {
                    TypeMirror builtType = type.getTypeArguments().get(0);
                    return new BuilderTypeInfo(typeUtils.erasure(builtType).toString());
                }
            }
        }
        BuilderTypeInfo found = null;
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
        return new BuilderTypeInfo();
    }

    private void addInterfaces(ConfiguredType type, TypeElement classElement) {
        List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            TypeElement interfaceElement = (TypeElement) typeUtils.asElement(typeUtils.erasure(anInterface));
            if (interfaceElement.getAnnotation(Configured.class) != null) {
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
            if (superclass.getAnnotation(Configured.class) != null) {
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
        List<? extends AnnotationMirror> allAnnotationMirrors = elementUtils.getAllAnnotationMirrors(aClass);
        for (AnnotationMirror mirror : allAnnotationMirrors) {
            if (mirror.getAnnotationType().toString().equals(Configured.class.getName())) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = mirror.getElementValues();
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("provides")) {
                        List<String> result = new LinkedList<>();
                        ((List<AnnotationValue>) entry.getValue().getValue())
                                .stream()
                                .map(AnnotationValue::getValue)
                                .map(Object::toString)
                                .forEach(result::add);
                        return result;
                    }
                }
            }
        }
        return List.of();
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
                // public
                .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                // not static
                .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
                // return the same type (e.g. Builder)
                .filter(it -> isBuilderMethod(builderElement, it))
                .forEach(it -> processBuilderMethod(it, type, className));
        List<? extends TypeMirror> interfaces = builderElement.getInterfaces();
        for (TypeMirror anInterface : interfaces) {
            Element interfaceElement = typeUtils.asElement(anInterface);
            if (interfaceElement.getAnnotation(Configured.class) == null) {
                continue;
            }
            String name = typeUtils.erasure(anInterface).toString();

            // it should be in current sources
            elementUtils.getAllMembers((TypeElement) interfaceElement)
                    .stream()
                    .filter(it -> it.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    // public
                    .filter(it -> it.getModifiers().contains(Modifier.PUBLIC))
                    // not static
                    .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
                    // return the same type (e.g. Builder)
                    .filter(it -> typeUtils.isSameType(builderElement.asType(), it.getReturnType()))
                    .forEach(it -> processBuilderMethod(it, type, className));
        }
    }

    private boolean isBuilderMethod(TypeElement builderElement, ExecutableElement it) {
        TypeMirror builderType = builderElement.asType();
        TypeMirror methodReturnType = it.getReturnType();
        if (typeUtils.isSameType(builderType, methodReturnType)) {
            return true;
        }
        return it.getAnnotation(ConfiguredOption.class) != null;
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
                    ConfiguredOptionData data = createConfiguredOptionData(option);

                    if (data.name == null || data.name.isBlank()) {
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

                    ConfiguredProperty prop = new ConfiguredProperty((String) null,
                                                                     data.name,
                                                                     data.description,
                                                                     data.defaultValue,
                                                                     data.type,
                                                                     data.experimental,
                                                                     !data.required,
                                                                     data.kind,
                                                                     data.provider,
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
            ConfiguredOptionData annotation = createConfiguredOptionData(option);

            String name = key(annotation.name, element);
            String description = description(annotation.description, element);
            String defaultValue = defaultValue(annotation.defaultValue);
            boolean experimental = annotation.experimental;
            OptionType type = type(annotation, element);
            boolean optional = defaultValue != null || !annotation.required;

            String[] paramTypes = methodParams(element);

            ProducerMethod builderMethod = new ProducerMethod(false,
                                                              className,
                                                              element.getSimpleName().toString(),
                                                              paramTypes);

            ConfiguredProperty property = new ConfiguredProperty(builderMethod,
                                                                 name,
                                                                 description,
                                                                 defaultValue,
                                                                 type.elementType,
                                                                 experimental,
                                                                 optional,
                                                                 type.kind,
                                                                 annotation.provider,
                                                                 annotation.allowedValues);
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
        return UNCONFIGURED.equals(defaultValue) ? null : defaultValue;
    }

    private OptionType type(ConfiguredOptionData annotation, ExecutableElement element) {
        if (annotation.type == null || annotation.type.equals(ConfiguredOption.class.getName())) {
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

                if (typeUtils.isSameType(erasedType, erasedListType) || typeUtils.isSameType(erasedType, erasedSetType)) {
                    DeclaredType type = (DeclaredType) paramType;
                    TypeMirror genericType = type.getTypeArguments().get(0);
                    return new OptionType(genericType.toString(), ConfiguredOption.Kind.LIST);
                }

                if (typeUtils.isSameType(erasedType, erasedMapType)) {
                    DeclaredType type = (DeclaredType) paramType;
                    TypeMirror genericType = type.getTypeArguments().get(1);
                    return new OptionType(genericType.toString(), ConfiguredOption.Kind.MAP);
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

    private String javadoc(String docComment) {
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

            JsonBuilderFactory jsonBuilderFactory = Json.createBuilderFactory(Map.of());

            /*
             The root of the json file is an array that contains module entries
             This is to allow merging of files - such as when we would want to create on-the-fly
             JSON for a project with only its dependencies.
             */
            JsonArrayBuilder moduleArray = jsonBuilderFactory.createArrayBuilder();

            for (var module : moduleTypes.entrySet()) {
                String moduleName = module.getKey();
                var types = module.getValue();
                JsonArrayBuilder typeArray = jsonBuilderFactory.createArrayBuilder();
                types.forEach(it -> {
                    newOptions.get(it).write(jsonBuilderFactory, typeArray);
                });
                moduleArray.add(jsonBuilderFactory.createObjectBuilder()
                                        .add("module", moduleName)
                                        .add("types", typeArray));
            }

            JsonWriter writer = Json.createWriter(metaWriter);
            writer.write(moduleArray.build());
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
        List<? extends AnnotationMirror> allAnnotationMirrors = elementUtils.getAllAnnotationMirrors(element);
        List<AnnotationMirror> options = new LinkedList<>();
        for (AnnotationMirror annotMirror : allAnnotationMirrors) {
            if (ConfiguredOptions.class.getName().equals(annotMirror.getAnnotationType().toString())) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotMirror
                        .getElementValues();
                elementValues.forEach((key, value) -> {
                    if (key.getSimpleName().contentEquals("value")) {
                        List<AnnotationMirror> list = (List<AnnotationMirror>) value.getValue();
                        options.addAll(list);
                    }
                });
                break;
            } else if (ConfiguredOption.class.getName().equals(annotMirror.getAnnotationType().toString())) {
                options.add(annotMirror);
                break;
            }
        }
        return options;
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

    @SuppressWarnings("unchecked")
    ConfiguredOptionData createConfiguredOptionData(AnnotationMirror configuredMirror) {
        ConfiguredOptionData result = new ConfiguredOptionData();

        Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = configuredMirror.getElementValues();

        TypeElement enumType = null;

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
            Name key = entry.getKey().getSimpleName();
            Object value = entry.getValue().getValue();

            if (key.contentEquals("value")) {
                result.name = (String) value;
            } else if (key.contentEquals("description")) {
                result.description = (String) value;
            } else if (key.contentEquals("defaultValue")) {
                result.defaultValue = (String) value;
            } else if (key.contentEquals("experimental")) {
                result.experimental = (Boolean) value;
            } else if (key.contentEquals("required")) {
                result.required = (Boolean) value;
            } else if (key.contentEquals("type")) {
                TypeMirror typeMirror = (TypeMirror) value;
                Element element = typeUtils.asElement(typeMirror);
                if (element.getKind() == ElementKind.ENUM) {
                    enumType = (TypeElement) element;
                }

                result.type = value.toString();
            } else if (key.contentEquals("kind")) {
                result.kind = ConfiguredOption.Kind.valueOf(value.toString());
            } else if (key.contentEquals("provider")) {
                result.provider = (Boolean) value;
            } else if (key.contentEquals("allowedValues")) {
                ((List<AnnotationMirror>) value).stream()
                        .map(AllowedValue::create)
                        .forEach(result.allowedValues::add);

            }
        }
        if (result.allowedValues.isEmpty() && (enumType != null)) {
            enumType.getEnclosedElements()
                    .stream()
                    .filter(element -> element.getKind().equals(ElementKind.ENUM_CONSTANT))
                    .forEach(element -> result.allowedValues
                            .add(new AllowedValue(element.toString(), javadoc(elementUtils.getDocComment(element)))));

        }

        return result;
    }

    private static final class OptionType {
        private final String elementType;
        private final ConfiguredOption.Kind kind;

        private OptionType(String elementType, ConfiguredOption.Kind kind) {
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
        private ConfiguredOption.Kind kind = ConfiguredOption.Kind.VALUE;
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
