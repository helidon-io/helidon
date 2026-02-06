/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.builder.codegen.spi.BuilderCodegenExtensionProvider;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.FilerTextResource;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Errors;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.metadata.MetadataConstants;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_EXTENSION;
import static io.helidon.builder.codegen.Types.PROTOTYPE_EXTENSIONS;
import static io.helidon.builder.codegen.Types.SERVICE_REGISTRY;

/*
Each option can have the following methods:
- Blueprint method - defined by user, cannot be changed (optional, we can have properties defined in extensions)
- Prototype method - generated getter method on prototype interface (always exists)
- BuilderBase setter declared - setter with the type declared - replaces value
- BuilderBase setter value - setter with the type of the value - for Optional<X> this is setter(X)
- BuilderBase setter add declared - for set, list, map - adds values to existing values
- BuilderBase setter add value - for set, list to add a single value to collection (for @Singular)
- BuilderBase setter add key/value - for Map<String, List<...>> - adds a value to the collection for the provided key
- BuilderBase setter add key/values - for Map<String, List<...>> - adds values to the collection for the provided key
- BuilderBase setter put key/value - for Map, replaces current mapping (for @Singular)
- BuilderBase getter - a getter that returns the declared type or an Optional<DeclaredType> for mandatory fields

For annotation purposes:
- getter is the method on prototype, implemented by Impl
- setter is the setter with value (or declared if the same) - i.e. if we have an optional, setter is considered only the
non-optional one
 */
class BuilderCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(BuilderCodegen.class);

    // all blueprint types (for validation)
    private final Set<PrototypeInfo> blueprintTypes = new HashSet<>();
    // all types from service loader that should be supported by ServiceRegistry
    private final Set<String> serviceLoaderContracts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private final CodegenContext ctx;
    private final List<BuilderCodegenExtensionProvider> extensions;

    BuilderCodegen(CodegenContext ctx) {
        this.ctx = ctx;
        this.extensions = HelidonServiceLoader.create(ServiceLoader.load(BuilderCodegenExtensionProvider.class,
                                                                         BuilderCodegen.class.getClassLoader()))
                .asList();
    }

    /**
     *
     * @param classModel     prototype class model builder
     * @param customMethods  custom methods to code generate
     * @param implementation true for implementation, false for prototype
     */
    static void generateCustomPrototypeMethods(ClassBase.Builder<?, ?> classModel,
                                               List<GeneratedMethod> customMethods,
                                               boolean implementation) {

        for (GeneratedMethod customMethod : customMethods) {
            // we can generate everything except for toString(), hashCode(), and equals(Object)
            if (onlyImplMethod(customMethod)) {
                if (implementation) {
                    Utils.addGeneratedMethod(classModel, customMethod);
                }
            } else {
                if (!implementation) {
                    Utils.addGeneratedMethod(classModel, customMethod);
                }
            }
        }
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> blueprints = roundContext.annotatedTypes(Types.PROTOTYPE_BLUEPRINT);

        List<TypeInfo> blueprintInterfaces = blueprints.stream()
                .filter(it -> it.kind() == ElementKind.INTERFACE)
                .toList();

        for (TypeInfo blueprintInterface : blueprintInterfaces) {
            process(roundContext, blueprintInterface);
        }
    }

    @Override
    public void processingOver(RoundContext roundContext) {
        process(roundContext);

        // now create service.loader
        updateServiceLoaderResource();

        // we must collect validation information after all types are generated - so
        // we also listen on @Generated, so there is another round of annotation processing where we have all
        // types nice and ready
        List<ValidationTask> validationTasks = new ArrayList<>(addBlueprintsForValidation(roundContext, this.blueprintTypes));

        Errors.Collector collector = Errors.collector();
        for (ValidationTask task : validationTasks) {
            task.validate(collector);
        }

        Errors errors = collector.collect();
        if (errors.hasFatal()) {
            for (Errors.ErrorMessage error : errors) {
                CodegenEvent.Builder builder = CodegenEvent.builder()
                        .message(error.getMessage().replace('\n', ' '))
                        .addObject(error.getSource());

                switch (error.getSeverity()) {
                case FATAL -> builder.level(System.Logger.Level.ERROR);
                case WARN -> builder.level(System.Logger.Level.WARNING);
                case HINT -> builder.level(System.Logger.Level.INFO);
                default -> builder.level(System.Logger.Level.DEBUG);
                }

                ctx.logger().log(builder.build());
            }
        }
    }

    private static void addCreateDefaultMethod(List<OptionHandler> options,
                                               ClassModel.Builder classModel,
                                               PrototypeInfo prototype,
                                               String ifaceName,
                                               String typeArgumentString,
                                               List<TypeArgument> typeArguments) {

        /*
          static X create()
         */
        boolean noRequired = noRequired(options);

        // in case there is a builder decorator, we include this method, as missing values could be fixed there
        if (noRequired || prototype.builderDecorator().isPresent()) {
            classModel.addMethod(builder -> {
                builder.isStatic(true)
                        .name("create")
                        .description("Create a new instance with default values.")
                        .returnType(prototype.prototypeType(), "a new instance")
                        .addContentLine("return " + ifaceName + "." + typeArgumentString + "builder().buildPrototype();");
                typeArguments.forEach(builder::addGenericArgument);
            });
        }
    }

    private static void addCreateFromConfigMethod(PrototypeConfigured configuredData,
                                                  TypeName prototype,
                                                  List<TypeArgument> typeArguments,
                                                  String ifaceName,
                                                  String typeArgumentString,
                                                  ClassModel.Builder classModel) {

        Method.Builder method = Method.builder()
                .accessModifier(configuredData.createAccessModifier())
                .name("create")
                .isStatic(true)
                .description("Create a new instance from configuration.")
                .returnType(prototype, "a new instance configured from configuration")
                .addParameter(paramBuilder -> paramBuilder.type(Types.CONFIG)
                        .name("config")
                        .description("used to configure the new instance"));
        typeArguments.forEach(method::addGenericArgument);
        method.addContent("return ")
                .addContent(ifaceName)
                .addContent(".")
                .addContent(typeArgumentString)
                .addContentLine("builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".config(config)")
                .addContentLine(".buildPrototype();")
                .decreaseContentPadding()
                .decreaseContentPadding();
        classModel.addMethod(method);

        // backward compatibility
        Method.Builder commonMethod = Method.builder()
                .name("create")
                .isStatic(true)
                .returnType(prototype)
                .addParameter(paramBuilder -> paramBuilder.type(Types.COMMON_CONFIG)
                        .name("config"))
                .javadoc(Javadoc.builder()
                                 .add("Create a new instance from configuration.")
                                 .returnDescription("a new instance configured from configuration")
                                 .addParameter("config", "used to configure the new instance")
                                 .addTag("deprecated", "use {@link #create(" + Types.CONFIG.fqName() + ")}")
                                 .build())
                .addContent("return create(")
                .addContent(Types.CONFIG)
                .addContentLine(".config(config));")
                .addAnnotation(Annotations.DEPRECATED);
        typeArguments.forEach(commonMethod::addGenericArgument);
        classModel.addMethod(commonMethod);
    }

    private static void addCopyBuilderMethod(ClassModel.Builder classModel,
                                             TypeName builderTypeName,
                                             TypeName prototype,
                                             List<TypeArgument> typeArguments,
                                             String ifaceName,
                                             String typeArgumentString) {
        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder from an existing instance.")
                    .returnType(builderTypeName, "a builder based on an instance")
                    .addParameter(paramBuilder -> paramBuilder.type(prototype)
                            .name("instance")
                            .description("an existing instance used as a base for the builder"));
            typeArguments.forEach(builder::addGenericArgument);
            builder.addContentLine("return " + ifaceName + "." + typeArgumentString + "builder().from(instance);");
        });
    }

    private static void addBuilderMethod(ClassModel.Builder classModel,
                                         TypeName builderTypeName,
                                         List<TypeArgument> typeArguments,
                                         String ifaceName) {

        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder to customize configuration.")
                    .returnType(builderTypeName, "a new builder");
            typeArguments.forEach(builder::addGenericArgument);
            if (typeArguments.isEmpty()) {
                builder.addContentLine("return new " + ifaceName + ".Builder();");
            } else {
                builder.addContentLine("return new " + ifaceName + ".Builder<>();");
            }
        });
    }

    private static void generateCustomConstant(ClassModel.Builder classModel, PrototypeConstant customConstant) {
        classModel.addField(constant -> constant
                .type(customConstant.type())
                .name(customConstant.name())
                .javadoc(customConstant.javadoc())
                .update(customConstant.content()::accept));

    }

    private static void generateCustomMethods(ClassModel.Builder classModel,
                                              List<GeneratedMethod> customMethods) {

        for (GeneratedMethod customMethod : customMethods) {
            Utils.addGeneratedMethod(classModel, customMethod);
        }
    }

    private static boolean onlyImplMethod(GeneratedMethod customMethod) {
        var method = customMethod.method();
        var methodName = method.elementName();
        List<TypedElementInfo> params = method.parameterArguments();

        if (methodName.equals("toString") || methodName.equals("hashCode")) {
            return params.isEmpty();
        }
        if (methodName.equals("equals")) {
            return params.size() == 1 && params.getFirst().typeName().equals(TypeNames.OBJECT);
        }
        return false;
    }

    private static boolean noRequired(List<OptionHandler> options) {
        return options.stream()
                .map(OptionHandler::option)
                .noneMatch(OptionInfo::required);
    }

    private static boolean hasRegistryService(List<OptionInfo> options) {
        return options.stream()
                .anyMatch(OptionInfo::registryService);
    }

    private void updateServiceLoaderResource() {
        CodegenFiler filer = ctx.filer();
        String moduleName = ctx.moduleName().orElse(null);
        String resourceLocation = MetadataConstants.LOCATION
                + (moduleName == null ? "" : "/" + moduleName)
                + "/" + MetadataConstants.SERVICE_LOADER_FILE;
        FilerTextResource serviceLoaderResource = filer.textResource(resourceLocation);
        List<String> lines = new ArrayList<>(serviceLoaderResource.lines());
        if (lines.isEmpty()) {
            lines.add("# List of service contracts we want to support either from service registry, or from service loader");
        }
        boolean modified = false;
        for (String serviceLoaderContract : this.serviceLoaderContracts) {
            if (!lines.contains(serviceLoaderContract)) {
                modified = true;
                lines.add(serviceLoaderContract);
            }
        }

        if (modified) {
            serviceLoaderResource.lines(lines);
            serviceLoaderResource.write();
            filer.manifest()
                    .add(resourceLocation);
        }
    }

    private void process(RoundContext roundContext, TypeInfo blueprint) {
        /*
         All information about the prototype itself except for options
         */
        PrototypeInfo tmpPrototypeInfo = FactoryPrototypeInfo.create(roundContext, blueprint);

        // find all extensions, as these may modify handling of our types
        List<BuilderCodegenExtension> extensions = findExtensions(tmpPrototypeInfo);

        // an extension may modify the prototype info (i.e. to add custom annotations etc).
        for (BuilderCodegenExtension extension : extensions) {
            tmpPrototypeInfo = extension.prototypeInfo(tmpPrototypeInfo);
        }

        /*
         We must identify the first prototype/blueprint we extend, and then discover all options on interfaces we extend
         that are not prototype/blueprint; then we find options that are NOT defined on the super prototype/blueprint,
         unless we have a default value (as in that case, we will need to override it in builder constructor)
         We cannot implement/extend more than one direct prototype/blueprint, as our builder cannot extend more than one class,
            but call the one from the supertype
        */

        List<OptionInfo> newOptions = new ArrayList<>();
        List<OptionInfo> existingOptions = new ArrayList<>();

        FactoryOption.options(ctx, roundContext, tmpPrototypeInfo, newOptions, existingOptions);

        for (BuilderCodegenExtension extension : extensions) {
            newOptions = extension.options(tmpPrototypeInfo, newOptions);
        }

        // now we have final prototype info - processed by all extensions, next we start collecting options
        PrototypeInfo prototypeInfo = fixFactoryMethods(tmpPrototypeInfo, newOptions);
        // add for validation, but only once we have all the information
        blueprintTypes.add(prototypeInfo);

        // now for each provider, add discoverServices builder option (this is required for our code to work)
        List<OptionInfo> discoverServicesOptions = newOptions.stream()
                .filter(it -> it.provider().isPresent() || it.registryService())
                .map(it -> {
                    boolean defaultValue = it.provider().map(OptionProvider::discoverServices).orElse(true);

                    String name = it.name() + "DiscoverServices";
                    String setterName = it.setterName() + "DiscoverServices";
                    String getterName = it.getterName() + "DiscoverServices";
                    return OptionInfo.builder()
                            .accessModifier(it.accessModifier())
                            .description("Service discovery flag for {@link #" + it.getterName()
                                                 + "()}. If set to {@code true}, services will be discovered from Java service "
                                                 + "loader, or Helidon ServiceRegistry.")
                            .paramDescription("whether to enabled automatic service discovery")
                            .name(name)
                            .setterName(setterName)
                            .getterName(getterName)
                            .builderOptionOnly(true)
                            .declaredType(TypeNames.PRIMITIVE_BOOLEAN)
                            .defaultValue(defaultConsumer -> defaultConsumer.addContent(String.valueOf(defaultValue)))
                            .update(optionBuilder -> copyConfiguredForDiscoverServices(it, optionBuilder))
                            .update(optionBuilder -> it.deprecation().ifPresent(optionBuilder::deprecation))
                            .build();
                })
                .toList();

        // ensure mutability
        newOptions = new ArrayList<>(newOptions);
        newOptions.addAll(discoverServicesOptions);
        configOption(prototypeInfo, newOptions, existingOptions);
        serviceRegistryOption(prototypeInfo, newOptions);

        /*
        We may have new options that override existing option's:
        - default value: add to builder base constructor, to call setter after super is constructed
        - annotations: add the methods explicitly, call super.getter, super.setter where makes sense
        - method names: add the methods explicitly, call super.getter, super.setter where makes sense
         */

        // now we have final option infos - processed by all extension, next we can start building

        Map<String, List<OptionInfo>> existingOptionMap = new LinkedHashMap<>();
        Map<String, List<OptionInfo>> newOptionMap = new LinkedHashMap<>();

        for (OptionInfo existingOption : existingOptions) {
            existingOptionMap.computeIfAbsent(existingOption.name(), k -> new ArrayList<>())
                    .add(existingOption);
        }
        for (OptionInfo newOption : newOptions) {
            newOptionMap.computeIfAbsent(newOption.name(), k -> new ArrayList<>())
                    .add(newOption);
        }

        List<OptionInfo> options = new ArrayList<>();
        List<NewDefault> newDefaults = new ArrayList<>();

        for (var optionEntry : newOptionMap.entrySet()) {
            if (existingOptionMap.containsKey(optionEntry.getKey())) {
                // this option already exists on super-prototype, handle it (use first default we find)
                optionEntry.getValue()
                        .stream()
                        .filter(it -> it.defaultValue().isPresent())
                        .findFirst()
                        .ifPresent(it -> newDefaults.add(new NewDefault(optionEntry.getKey(),
                                                                        setterName(prototypeInfo,
                                                                                   existingOptionMap.get(optionEntry.getKey())),
                                                                        it.defaultValue().get())));
            } else {
                // this is a net-new option
                List<OptionInfo> optionInfos = optionEntry.getValue();
                if (optionInfos.size() == 1) {
                    options.add(optionInfos.getFirst());
                } else {
                    // merge options from multiple declaring interfaces (maybe blueprint and prototype?)
                    options.add(mergeOptions(optionInfos));
                }
            }
        }

        // now we prepare all methods that are related to options
        var optionHandlers = options.stream()
                .map(it -> OptionHandler.create(extensions, prototypeInfo, it))
                .toList();

        generatePrototype(roundContext, extensions, prototypeInfo, optionHandlers, newDefaults);
    }

    private void serviceRegistryOption(PrototypeInfo prototypeInfo, List<OptionInfo> newOptions) {
        boolean registrySupport = prototypeInfo.registrySupport() || hasRegistryService(newOptions);

        if (!registrySupport) {
            return;
        }

        boolean style = prototypeInfo.recordStyle();
        String getter = style ? "serviceRegistry" : "getServiceRegistry";
        String setter = style ? "serviceRegistry" : "setServiceRegistry";

        newOptions.add(OptionInfo.builder()
                               .name("serviceRegistry")
                               .declaredType(SERVICE_REGISTRY)
                               .getterName(getter)
                               .setterName(setter)
                               .includeInEqualsAndHashCode(false)
                               .includeInToString(false)
                               .builderOptionOnly(true)
                               .accessModifier(AccessModifier.PUBLIC)
                               .description("Service registry used to discover providers and services.\n"
                                                    + "Provide an explicit registry instance to use.\n"
                                                    + "<p>\n"
                                                    + "If not configured, the {@link "
                                                    + Types.GLOBAL_SERVICE_REGISTRY.fqName()
                                                    + "} would be used to discover services.")
                               .paramDescription("service registry to use")
                               .build());
    }

    private void configOption(PrototypeInfo prototypeInfo, List<OptionInfo> newOptions, List<OptionInfo> existingOptions) {
        if (prototypeInfo.configured().isEmpty()) {
            return;
        }
        // discover if either existing or new options contain config option
        if (hasConfigOption(newOptions)) {
            return;
        }

        if (hasConfigOption(existingOptions)) {
            return;
        }

        if (prototypeInfo.superPrototype().isPresent()) {
            // assume configurable
            return;
        }

        // we must add it, for now common config to have backward compatibility
        boolean style = prototypeInfo.recordStyle();
        String getter = style ? "config" : "getConfig";
        String setter = style ? "config" : "setConfig";

        newOptions.add(OptionInfo.builder()
                               .name("config")
                               .declaredType(COMMON_CONFIG)
                               .getterName(getter)
                               .setterName(setter)
                               .includeInEqualsAndHashCode(false)
                               .includeInToString(false)
                               .builderOptionOnly(true)
                               .accessModifier(AccessModifier.PROTECTED)
                               .description("Configuration used to configure this instance.")
                               .paramDescription("config instance")
                               .build());
    }

    private boolean hasConfigOption(List<OptionInfo> options) {
        return options.stream()
                .filter(it -> it.name().equals("config"))
                .anyMatch(it -> {
                    // we only support Config, Optional<Config> for both common and config config
                    var optionType = it.declaredType();
                    if (optionType.equals(CONFIG) || optionType.equals(COMMON_CONFIG)) {
                        return true;
                    }
                    if (optionType.isOptional()) {
                        optionType = optionType.typeArguments().getFirst();
                        if (optionType.equals(CONFIG) || optionType.equals(COMMON_CONFIG)) {
                            return true;
                        }
                    }
                    throw new CodegenException("An option named \"config\" in a configured blueprint must be of type "
                                                       + CONFIG.fqName() + " or its optional, but is: "
                                                       + it.declaredType().fqName(),
                                               it.interfaceMethod().map(TypedElementInfo::originatingElementValue)
                                                       .orElse(it.name()));
                });
    }

    @SuppressWarnings({"removal", "deprecation"})
    private PrototypeInfo fixFactoryMethods(PrototypeInfo tmpPrototypeInfo, List<OptionInfo> newOptions) {
        // for backward compatibility, we support factory methods of both runtime type and prototype to be mixed
        // here, as we know all option types, we can separate them

        TypeName prototypeType = tmpPrototypeInfo.prototypeType();

        var builder = PrototypeInfo.builder(tmpPrototypeInfo);
        List<GeneratedMethod> prototypeFactories = new ArrayList<>(tmpPrototypeInfo.prototypeFactories());

        for (DeprecatedFactoryMethod factoryMethod : tmpPrototypeInfo.deprecatedFactoryMethods()) {
            // if the factory method is void or does not have one parameter, it is for sure a prototype factory
            TypedElementInfo method = factoryMethod.method();
            TypeName returnType = method.typeName();

            if (returnType.unboxed().equals(TypeNames.PRIMITIVE_VOID)
                    || method.parameterArguments().size() != 1
                    || Utils.typesEqual(returnType, prototypeType)) {
                prototypeFactories.add(GeneratedMethods.createFactoryMethod(factoryMethod.declaringType(),
                                                                            factoryMethod.method(),
                                                                            List.of()));
                continue;
            }

            if (newOptions.stream()
                    .filter(it -> Utils.typesEqual(returnType, Utils.realType(it.declaredType()))
                            || Utils.resolvedTypesEqual(returnType, it.declaredType()))
                    .findAny()
                    .isEmpty()) {

                // yep, this is a factory method to be generated
                prototypeFactories.add(GeneratedMethods.createFactoryMethod(factoryMethod.declaringType(),
                                                                            factoryMethod.method(),
                                                                            List.of()));
            }
        }

        return builder
                .prototypeFactories(prototypeFactories)
                .build();
    }

    private void copyConfiguredForDiscoverServices(OptionInfo providerOption, OptionInfo.Builder optionBuilder) {
        if (providerOption.configured().isEmpty()) {
            return;
        }
        OptionConfigured configured = providerOption.configured().get();
        optionBuilder.configured(cfg -> cfg
                .merge(configured.merge())
                .configKey(configured.configKey() + "-discover-services")
        );
    }

    private OptionInfo mergeOptions(List<OptionInfo> optionInfos) {
        for (OptionInfo optionInfo : optionInfos) {
            if (optionInfo.interfaceMethod().isPresent()) {
                var enclosing = optionInfo.interfaceMethod().get().enclosingType();
                if (enclosing.isPresent() && enclosing.get().className().endsWith("Blueprint")) {
                    // always prefer blueprint options
                    return optionInfo;
                }
            }
        }
        // this means all the options are non-blueprint, so we are not customizing anything...
        // just use the first one
        return optionInfos.getFirst();
    }

    private String setterName(PrototypeInfo prototypeInfo, List<OptionInfo> optionInfos) {
        boolean recordStyle = prototypeInfo.recordStyle();
        String first = null;
        for (OptionInfo optionInfo : optionInfos) {
            String setterName = optionInfo.setterName();
            if (first == null) {
                first = setterName;
            }
            if (setterName.startsWith("set") && setterName.length() > 3 && Character.isUpperCase(setterName.charAt(3))) {
                if (!recordStyle) {
                    return setterName;
                }
            } else {
                if (recordStyle) {
                    return setterName;
                }
            }
        }
        return first;
    }

    private void generatePrototype(RoundContext ctx,
                                   List<BuilderCodegenExtension> extensions,
                                   PrototypeInfo prototypeInfo,
                                   List<OptionHandler> options,
                                   List<NewDefault> newDefaults) {

        TypeInfo blueprint = prototypeInfo.blueprint();
        TypeName prototype = prototypeInfo.prototypeType();
        String ifaceName = prototype.className();
        List<TypeName> typeGenericArguments = blueprint.typeName().typeArguments();
        String typeArgumentString = createTypeArgumentString(typeGenericArguments);
        var javadoc = prototypeInfo.javadoc();
        List<TypeArgument> typeArguments = typeGenericArguments
                .stream()
                .map(it -> {
                    var builder = TypeArgument.builder()
                            .token(it.className());
                    if (!it.upperBounds().isEmpty()) {
                        it.upperBounds().forEach(builder::addBound);
                    }
                    List<String> tokenJavadoc = javadoc.genericsTokens().get(it.className());
                    if (tokenJavadoc != null) {
                        builder.description(tokenJavadoc);
                    }
                    return builder.build();
                })
                .toList();

        // prototype interface (with inner classes: BuilderBase, Builder, and Impl)
        ClassModel.Builder classModel = ClassModel.builder()
                .type(prototypeInfo.prototypeType())
                .classType(ElementKind.INTERFACE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 blueprint.typeName(),
                                                 prototype))
                .accessModifier(prototypeInfo.accessModifier());

        typeArguments.forEach(classModel::addGenericArgument);

        prototypeInfo.annotations()
                .forEach(classModel::addAnnotation);

        classModel.addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                                 blueprint.typeName(),
                                                                 prototype,
                                                                 "1",
                                                                 ""));

        prototypeInfo.superTypes()
                .forEach(classModel::addInterface);

        prototypeInfo.constants()
                .forEach(constant -> generateCustomConstant(classModel, constant));

        TypeName builderTypeName = TypeName.builder()
                .from(TypeName.create(prototype.fqName() + ".Builder"))
                .typeArguments(prototype.typeArguments())
                .build();

        // static Builder builder()
        addBuilderMethod(classModel, builderTypeName, typeArguments, ifaceName);

        // static Builder builder(T instance)
        addCopyBuilderMethod(classModel, builderTypeName, prototype, typeArguments, ifaceName, typeArgumentString);

        if (prototypeInfo.configured().isPresent()) {
            // static T create(Config config)
            addCreateFromConfigMethod(prototypeInfo.configured().get(),
                                      prototype,
                                      typeArguments,
                                      ifaceName,
                                      typeArgumentString,
                                      classModel);
        }

        // static X create()
        if (prototypeInfo.createEmptyCreate()) {
            addCreateDefaultMethod(options,
                                   classModel,
                                   prototypeInfo,
                                   ifaceName,
                                   typeArgumentString,
                                   typeArguments);
        }

        generateCustomMethods(classModel, prototypeInfo.prototypeFactories());
        generateCustomPrototypeMethods(classModel, prototypeInfo.prototypeMethods(), false);

        // re-create all blueprint methods to have correct javadoc references
        generatePrototypeMethods(classModel, options);

        List<OptionInfo> optionList = options.stream()
                .map(OptionHandler::option)
                .collect(Collectors.toUnmodifiableList());

        // abstract class BuilderBase...
        GenerateAbstractBuilder.generate(extensions,
                                         classModel,
                                         prototypeInfo,
                                         typeArguments,
                                         typeGenericArguments,
                                         options,
                                         newDefaults);

        // class Builder extends BuilderBase ...
        GenerateBuilder.generate(extensions,
                                 classModel,
                                 prototypeInfo,
                                 typeArguments,
                                 typeGenericArguments,
                                 options);

        classModel.javadoc(prototypeInfo.javadoc());
        for (BuilderCodegenExtension extension : extensions) {
            extension.updatePrototype(prototypeInfo,
                                      optionList,
                                      classModel);
        }
        if (prototypeInfo.builderAccessModifier() == AccessModifier.PUBLIC) {
            classModel.addJavadocTag("see", "#builder()");
        }
        if (noRequired(options)
                && prototypeInfo.createEmptyCreate()
                && prototypeInfo.builderAccessModifier() == AccessModifier.PUBLIC) {
            classModel.addJavadocTag("see", "#create()");
        }

        ctx.addGeneratedType(prototype,
                             classModel,
                             blueprint.typeName(),
                             blueprint.originatingElementValue());

        if (prototypeInfo.registrySupport()) {
            for (OptionHandler optionHandler : options) {
                OptionInfo option = optionHandler.option();
                if (option.provider().isPresent()) {
                    this.serviceLoaderContracts.add(option.provider().get().providerType().fqName());
                }
            }
        }
    }

    private List<BuilderCodegenExtension> findExtensions(PrototypeInfo prototypeInfo) {
        List<Annotation> extensions = prototypeInfo.blueprint().findAnnotation(PROTOTYPE_EXTENSIONS)
                .flatMap(it -> it.annotationValues())
                .orElseGet(List::of);
        if (!extensions.isEmpty()) {
            return extensionsForAnnotations(prototypeInfo, extensions);
        }
        return prototypeInfo.findAnnotation(PROTOTYPE_EXTENSION)
                .map(it -> extensionsForAnnotations(prototypeInfo, List.of(it)))
                .orElseGet(List::of);
    }

    private List<BuilderCodegenExtension> extensionsForAnnotations(PrototypeInfo prototypeInfo,
                                                                   List<Annotation> extensionAnnotations) {
        Set<TypeName> extensionTypes = extensionAnnotations.stream()
                .map(Annotation::typeValue)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        List<ExtensionAndTypes> found = new ArrayList<>();

        Set<TypeName> unsatisfiedTypes = new HashSet<>(extensionTypes);

        // each extension only ones
        for (BuilderCodegenExtensionProvider extension : this.extensions) {
            boolean matches = false;
            List<TypeName> matchedTypes = new ArrayList<>();
            for (TypeName extensionType : extensionTypes) {
                if (extension.supports(extensionType)) {
                    matches = true;
                    unsatisfiedTypes.remove(extensionType);
                    matchedTypes.add(extensionType);
                }
            }
            if (matches) {
                found.add(new ExtensionAndTypes(matchedTypes, extension));
            }
        }
        if (!unsatisfiedTypes.isEmpty()) {
            throw new CodegenException("Could not find builder codegen extension for types: " + unsatisfiedTypes
                                               + " (used in @Prototype.Extension)",
                                       prototypeInfo.blueprint());
        }

        return found.stream()
                .map(it -> it.provider().create(it.typeArray()))
                .toList();
    }

    private void generatePrototypeMethods(ClassModel.Builder classModel,
                                          List<OptionHandler> options) {

        for (OptionHandler optionHandler : options) {
            optionHandler.typeHandler()
                    .optionMethod(OptionMethodType.PROTOTYPE_GETTER)
                    .ifPresent(it -> Utils.addGeneratedMethod(classModel, it));

        }
    }

    private Collection<? extends ValidationTask> addBlueprintsForValidation(RoundContext roundContext,
                                                                            Set<PrototypeInfo> blueprints) {
        List<ValidationTask> result = new ArrayList<>();

        for (PrototypeInfo prototypeInfo : blueprints) {
            result.add(new ValidationTask.ValidateBlueprint(prototypeInfo));

            if (prototypeInfo.runtimeType().isPresent()) {
                result.add(new ValidationTask.ValidateBlueprintExtendsFactory(prototypeInfo,
                                                                              toTypeInfo(prototypeInfo.blueprint(),
                                                                                         prototypeInfo.runtimeType().get())));
            }
        }

        return result;
    }

    private TypeInfo toTypeInfo(TypeInfo typeInfo, TypeName typeName) {
        return ctx.typeInfo(typeName.genericTypeName())
                .orElseThrow(() -> new IllegalArgumentException("Type " + typeName.fqName() + " is not a valid type for Factory"
                                                                        + " declared on type " + typeInfo.typeName()
                        .fqName()));
    }

    private String createTypeArgumentString(List<TypeName> typeArguments) {
        if (!typeArguments.isEmpty()) {
            String arguments = typeArguments.stream()
                    .map(TypeName::className)
                    .collect(Collectors.joining(", "));
            return "<" + arguments + ">";
        }
        return "";
    }

    record NewDefault(String name, String setterName, Consumer<ContentBuilder<?>> defaultValue) {
    }

    private record ExtensionAndTypes(List<TypeName> types, BuilderCodegenExtensionProvider provider) {
        public TypeName[] typeArray() {
            return types.toArray(new TypeName[0]);
        }
    }
}
