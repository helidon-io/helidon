/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.builder.Builder;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;

import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.BUILDER_ANNO_TYPE_NAME;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.DEFAULT_INCLUDE_META_ATTRIBUTES;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.DEFAULT_LIST_TYPE;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.DEFAULT_MAP_TYPE;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.DEFAULT_SET_TYPE;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.SUPPORT_STREAMS_ON_BUILDER;
import static io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider.SUPPORT_STREAMS_ON_IMPL;

/**
 * Represents the context of the body being code generated.
 */
public class BodyContext {
    static final String TAG_META_PROPS = "__META_PROPS";

    private final boolean doingConcreteType;
    private final TypeName implTypeName;
    private final TypeInfo typeInfo;
    private final AnnotationAndValue builderTriggerAnnotation;
    private final Map<String, TypedElementName> map = new LinkedHashMap<>();
    private final List<TypedElementName> allTypeInfos = new ArrayList<>();
    private final List<String> allAttributeNames = new ArrayList<>();
    private final AtomicReference<TypeName> parentTypeName = new AtomicReference<>();
    private final AtomicReference<TypeName> parentAnnotationType = new AtomicReference<>();
    private final boolean hasStreamSupportOnImpl;
    private final boolean hasStreamSupportOnBuilder;
    private final boolean includeMetaAttributes;
    private final boolean requireLibraryDependencies;
    private final boolean beanStyleRequired;
    private final boolean allowNulls;
    private final boolean includeGeneratedAnnotation;
    private final String listType;
    private final String mapType;
    private final String setType;
    private final boolean hasParent;
    private final boolean hasAnyBuilderClashingMethodNames;
    private final boolean isExtendingAnAbstractClass;
    private final TypeName ctorBuilderAcceptTypeName;
    private final String genericBuilderClassDecl;
    private final String genericBuilderAliasDecl;
    private final String genericBuilderAcceptAliasDecl;
    private final String publicOrPackagePrivateDecl;
    private final TypeName interceptorTypeName;
    private final String interceptorCreateMethod;

    /**
     * Constructor.
     *
     * @param doingConcreteType true if the concrete type is being generated, otherwise the abstract class
     * @param implTypeName      the impl type name
     * @param typeInfo          the type info
     * @param builderTriggerAnnotation the builder annotation
     */
    BodyContext(boolean doingConcreteType,
                TypeName implTypeName,
                TypeInfo typeInfo,
                AnnotationAndValue builderTriggerAnnotation) {
        this.doingConcreteType = doingConcreteType;
        this.implTypeName = implTypeName;
        this.typeInfo = typeInfo;
        this.builderTriggerAnnotation = builderTriggerAnnotation;
        this.hasStreamSupportOnImpl = hasStreamSupportOnImpl(doingConcreteType, builderTriggerAnnotation);
        this.hasStreamSupportOnBuilder = hasStreamSupportOnBuilder(doingConcreteType, builderTriggerAnnotation);
        this.includeMetaAttributes = toIncludeMetaAttributes(builderTriggerAnnotation, typeInfo);
        this.requireLibraryDependencies = toRequireLibraryDependencies(builderTriggerAnnotation, typeInfo);
        this.beanStyleRequired = toRequireBeanStyle(builderTriggerAnnotation, typeInfo);
        this.allowNulls = toAllowNulls(builderTriggerAnnotation, typeInfo);
        this.includeGeneratedAnnotation = toIncludeGeneratedAnnotation(builderTriggerAnnotation, typeInfo);
        this.listType = toListImplType(builderTriggerAnnotation, typeInfo);
        this.mapType = toMapImplType(builderTriggerAnnotation, typeInfo);
        this.setType = toSetImplType(builderTriggerAnnotation, typeInfo);
        try {
            gatherAllAttributeNames(this, typeInfo);
        } catch (Exception e) {
            throw new IllegalStateException("Failed while processing: " + typeInfo.typeName(), e);
        }
        assert (allTypeInfos.size() == allAttributeNames.size());
        this.hasParent = (parentTypeName.get() != null && hasBuilder(typeInfo.superTypeInfo(), builderTriggerAnnotation));
        this.hasAnyBuilderClashingMethodNames = determineIfHasAnyClashingMethodNames();
        this.isExtendingAnAbstractClass = typeInfo.typeKind().equals(TypeInfo.KIND_CLASS);
        this.ctorBuilderAcceptTypeName = (hasParent)
                ? typeInfo.typeName()
                : (Objects.nonNull(parentAnnotationType.get()) && typeInfo.elementInfo().isEmpty()
                                ? typeInfo.superTypeInfo().orElseThrow().typeName() : typeInfo.typeName());
        this.genericBuilderClassDecl = "Builder";
        this.genericBuilderAliasDecl = ("B".equals(typeInfo.typeName().className())) ? "BU" : "B";
        this.genericBuilderAcceptAliasDecl = ("T".equals(typeInfo.typeName().className())) ? "TY" : "T";
        String interceptorType = searchForBuilderAnnotation("interceptor", builderTriggerAnnotation, typeInfo);
        this.interceptorTypeName = (interceptorType == null || Void.class.getName().equals(interceptorType))
                ? null : DefaultTypeName.createFromTypeName(interceptorType);
        String interceptorCreateMethod =
                searchForBuilderAnnotation("interceptorCreateMethod", builderTriggerAnnotation, typeInfo);
        this.interceptorCreateMethod = (interceptorCreateMethod == null || interceptorCreateMethod.isEmpty())
                ? null : interceptorCreateMethod;
        this.publicOrPackagePrivateDecl = (typeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE)
                                                   || typeInfo.modifierNames().isEmpty()
                                                   || typeInfo.modifierNames().contains(TypeInfo.MODIFIER_PUBLIC))
                                                        ? "public " : "";
    }

    @Override
    public String toString() {
        return implTypeName.toString();
    }

    /**
     * Returns true if we are currently processing the concrete builder type.
     *
     * @return true if we are processing the concrete type
     */
    public boolean doingConcreteType() {
        return doingConcreteType;
    }

    /**
     * Returns the impl type name.
     *
     * @return the type name
     */
    public TypeName implTypeName() {
        return implTypeName;
    }

    /**
     * Returns the type info.
     *
     * @return the type info
     */
    public TypeInfo typeInfo() {
        return typeInfo;
    }

    /**
     * Returns the builder annotation that triggered things.
     *
     * @return the builder annotation
     * @see io.helidon.builder.Builder
     */
    public AnnotationAndValue builderTriggerAnnotation() {
        return builderTriggerAnnotation;
    }

    /**
     * Returns the map of all type elements in the entire hierarchy.
     *
     * @return the map of elements by name
     */
    protected Map<String, TypedElementName> map() {
        return map;
    }

    /**
     * Returns the list of all type elements.
     *
     * @return the list of type elements
     */
    public List<TypedElementName> allTypeInfos() {
        return allTypeInfos;
    }

    /**
     * Returns the list of all attributes names.
     *
     * @return the list of attribute names
     */
    public List<String> allAttributeNames() {
        return allAttributeNames;
    }

    /**
     * Returns the parent type name of the builder.
     *
     * @return the parent type name
     */
    public AtomicReference<TypeName> parentTypeName() {
        return parentTypeName;
    }

    /**
     * Returns the parent annotation type.
     *
     * @return the parent annotation type
     */
    protected AtomicReference<TypeName> parentAnnotationType() {
        return parentAnnotationType;
    }

    /**
     * Returns true if there is stream support included on the generated class.
     *
     * @return true if stream support enabled
     */
    protected boolean hasStreamSupportOnImpl() {
        return hasStreamSupportOnImpl;
    }

    /**
     * Returns true if there is stream support included on the builder generated class.
     *
     * @return true if stream support enabled
     */
    protected boolean hasStreamSupportOnBuilder() {
        return hasStreamSupportOnBuilder;
    }

    /**
     * Returns true if meta attributes should be generated.
     * See {@link io.helidon.builder.Builder#includeMetaAttributes()}.
     *
     * @return true if meta attributes should be generated
     */
    protected boolean includeMetaAttributes() {
        return includeMetaAttributes;
    }

    /**
     * Returns true if Helidon library dependencies should be expected.
     * See {@link io.helidon.builder.Builder#requireLibraryDependencies()}.
     *
     * @return true if Helidon library dependencies are expected
     */
    protected boolean requireLibraryDependencies() {
        return requireLibraryDependencies;
    }

    /**
     * Returns true if bean "getter" and "is" style is required.
     * See {@link io.helidon.builder.Builder#requireBeanStyle()} .
     *
     * @return true if bean style is required
     */
    protected boolean beanStyleRequired() {
        return beanStyleRequired;
    }

    /**
     * Returns true if nulls are allowed.
     * See {@link io.helidon.builder.Builder#allowNulls()}.
     *
     * @return true if allow nulls
     */
    protected boolean allowNulls() {
        return allowNulls;
    }

    /**
     * Returns true if {@code jakarta.annotations.Generated} annotation should be generated.
     * See {@link io.helidon.builder.Builder#includeGeneratedAnnotation()}.
     *
     * @return true if the Generated annotation should be generated on the target beans
     */
    protected boolean includeGeneratedAnnotation() {
        return includeGeneratedAnnotation;
    }

    /**
     * Returns the list type generated.
     * See {@link io.helidon.builder.Builder#listImplType()}.
     *
     * @return the list type
     */
    public String listType() {
        return listType;
    }

    /**
     * Returns the map type generated.
     * See {@link io.helidon.builder.Builder#mapImplType()}.
     *
     * @return the map type
     */
    public String mapType() {
        return mapType;
    }

    /**
     * Returns the set type generated.
     * See {@link io.helidon.builder.Builder#setImplType()}.
     *
     * @return the set type
     */
    public String setType() {
        return setType;
    }

    /**
     * Returns true if the current type has a parent.
     *
     * @return true if current has parent
     */
    public boolean hasParent() {
        return hasParent;
    }

    /**
     * Returns true if any getter methods from the target clash with any builder method name.
     *
     * @return true if there is a clash
     */
    public boolean hasAnyBuilderClashingMethodNames() {
        return hasAnyBuilderClashingMethodNames;
    }

    /**
     * Returns true if this builder is extending an abstract class as a target.
     *
     * @return true if the target is an abstract class
     */
    public boolean isExtendingAnAbstractClass() {
        return isExtendingAnAbstractClass;
    }

    /**
     * Returns the streamable accept type of the builder and constructor.
     *
     * @return the builder accept type
     */
    protected TypeName ctorBuilderAcceptTypeName() {
        return ctorBuilderAcceptTypeName;
    }

    /**
     * Returns the generic declaration for the builder class type.
     *
     * @return the generic declaration
     */
    public String genericBuilderClassDecl() {
        return genericBuilderClassDecl;
    }

    /**
     * Returns the builder generics alias name for the type being built.
     *
     * @return the builder generics alias name
     */
    protected String genericBuilderAliasDecl() {
        return genericBuilderAliasDecl;
    }

    /**
     * Returns the builder generics alias name for the builder itself.
     *
     * @return the builder generics alias name
     */
    protected String genericBuilderAcceptAliasDecl() {
        return genericBuilderAcceptAliasDecl;
    }

    /**
     * Returns "public" or "" for public or package private declaration, accordingly.
     *
     * @return the modifier declaration
     */
    public String publicOrPackagePrivateDecl() {
        return publicOrPackagePrivateDecl;
    }
    /**
     * Returns the interceptor implementation type name.
     * See {@link io.helidon.builder.Builder#interceptor()}.
     *
     * @return the interceptor type name
     */
    public Optional<TypeName> interceptorTypeName() {
        return Optional.ofNullable(interceptorTypeName);
    }

    /**
     * Returns the interceptor create method name.
     * See {@link io.helidon.builder.Builder#interceptorCreateMethod()}.
     *
     * @return the interceptor create method name
     */
    public Optional<String> interceptorCreateMethod() {
        return Optional.ofNullable(interceptorCreateMethod);
    }

    /**
     * Checks whether there is an "other" method that matches the signature.
     *
     * @param name      the method name
     * @param typeInfo  the type info to check, which will look through the parent chain
     * @return true if there is any matches
     */
    public boolean hasOtherMethod(String name,
                                  TypeInfo typeInfo) {
        for (TypedElementName elem : typeInfo.otherElementInfo()) {
            if (elem.elementName().equals(name)) {
                return true;
            }
        }

        if (typeInfo.superTypeInfo().isPresent()) {
            return hasOtherMethod(name, typeInfo.superTypeInfo().get());
        }

        return false;
    }

    /**
     * returns the bean attribute name of a particular method.
     *
     * @param method                the method
     * @param isBeanStyleRequired   is bean style required
     * @return the bean attribute name
     */
    protected static String toBeanAttributeName(TypedElementName method,
                                                boolean isBeanStyleRequired) {
        AtomicReference<Optional<List<String>>> refAttrNames = new AtomicReference<>();
        BeanUtils.validateAndParseMethodName(method.elementName(), method.typeName().name(), isBeanStyleRequired, refAttrNames);
        List<String> attrNames = (refAttrNames.get().isEmpty()) ? List.of() : refAttrNames.get().get();
        if (!isBeanStyleRequired) {
            return (!attrNames.isEmpty()) ? attrNames.get(0) : method.elementName();
        }
        return Objects.requireNonNull(attrNames.get(0));
    }

    private static boolean hasStreamSupportOnImpl(boolean ignoreDoingConcreteClass,
                                                  AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_IMPL;
    }

    private static boolean hasStreamSupportOnBuilder(boolean ignoreDoingConcreteClass,
                                                     AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_BUILDER;
    }

    /**
     * In support of {@link io.helidon.builder.Builder#includeMetaAttributes()}.
     */
    private static boolean toIncludeMetaAttributes(AnnotationAndValue builderTriggerAnnotation,
                                                   TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("includeMetaAttributes", builderTriggerAnnotation, typeInfo);
        return (val == null) ? DEFAULT_INCLUDE_META_ATTRIBUTES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#requireLibraryDependencies()}.
     */
    private static boolean toRequireLibraryDependencies(AnnotationAndValue builderTriggerAnnotation,
                                                        TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireLibraryDependencies", builderTriggerAnnotation, typeInfo);
        return (val == null) ? DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#requireBeanStyle()}.
     */
    private static boolean toRequireBeanStyle(AnnotationAndValue builderTriggerAnnotation,
                                              TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireBeanStyle", builderTriggerAnnotation, typeInfo);
        return Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#allowNulls()}.
     */
    private static boolean toAllowNulls(AnnotationAndValue builderTriggerAnnotation,
                                        TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("allowNulls", builderTriggerAnnotation, typeInfo);
        return (val == null) ? Builder.DEFAULT_ALLOW_NULLS : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#includeGeneratedAnnotation()}.
     */
    private static boolean toIncludeGeneratedAnnotation(AnnotationAndValue builderTriggerAnnotation,
                                                        TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("includeGeneratedAnnotation", builderTriggerAnnotation, typeInfo);
        return (val == null) ? Builder.DEFAULT_INCLUDE_GENERATED_ANNOTATION : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#defineDefaultMethods()}.
     */
    private static boolean toDefineDefaultMethods(AnnotationAndValue builderTriggerAnnotation,
                                                  TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("defineDefaultMethods", builderTriggerAnnotation, typeInfo);
        return (val == null) ? Builder.DEFAULT_DEFINE_DEFAULT_METHODS : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#listImplType()}.
     */
    private static String toListImplType(AnnotationAndValue builderTriggerAnnotation,
                                         TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("listImplType", builderTriggerAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_LIST_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.builder.Builder#mapImplType()}.
     */
    private static String toMapImplType(AnnotationAndValue builderTriggerAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("mapImplType", builderTriggerAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_MAP_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.builder.Builder#setImplType()}.
     */
    private static String toSetImplType(AnnotationAndValue builderTriggerAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("setImplType", builderTriggerAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_SET_TYPE : type;
    }

    private static String searchForBuilderAnnotation(String key,
                                                     AnnotationAndValue builderTriggerAnnotation,
                                                     TypeInfo typeInfo) {
        String val = builderTriggerAnnotation.value(key).orElse(null);
        if (val != null) {
            return val;
        }

        if (!builderTriggerAnnotation.typeName().equals(BUILDER_ANNO_TYPE_NAME)) {
            AnnotationAndValue builderAnnotation = DefaultAnnotationAndValue
                    .findFirst(BUILDER_ANNO_TYPE_NAME.name(), typeInfo.annotations()).orElse(null);
            if (Objects.nonNull(builderAnnotation)) {
                val = builderAnnotation.value(key).orElse(null);
            }
        }

        if (val == null && typeInfo.superTypeInfo().isPresent()) {
            val = searchForBuilderAnnotation(key, builderTriggerAnnotation, typeInfo.superTypeInfo().get());
        }

        return val;
    }

    private static void gatherAllAttributeNames(BodyContext ctx,
                                                TypeInfo typeInfo) {
        TypeInfo superTypeInfo = typeInfo.superTypeInfo().orElse(null);
        if (Objects.nonNull(superTypeInfo)) {
            Optional<? extends AnnotationAndValue> superBuilderAnnotation = DefaultAnnotationAndValue
                    .findFirst(ctx.builderTriggerAnnotation.typeName().name(), superTypeInfo.annotations());
            if (superBuilderAnnotation.isEmpty()) {
                gatherAllAttributeNames(ctx, superTypeInfo);
            } else {
                populateMap(ctx.map, superTypeInfo, ctx.beanStyleRequired);
            }

            if (Objects.isNull(ctx.parentTypeName.get())
                    && superTypeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE)) {
                ctx.parentTypeName.set(superTypeInfo.typeName());
            } else if (Objects.isNull(ctx.parentAnnotationType.get())
                    && superTypeInfo.typeKind().equals(TypeInfo.KIND_ANNOTATION_TYPE)) {
                ctx.parentAnnotationType.set(superTypeInfo.typeName());
            }
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.beanStyleRequired);
            TypedElementName existing = ctx.map.get(beanAttributeName);
            if (Objects.nonNull(existing)
                    && BeanUtils.isBooleanType(method.typeName().name())
                    && method.elementName().startsWith("is")) {
                AtomicReference<Optional<List<String>>> alternateNames = new AtomicReference<>();
                BeanUtils.validateAndParseMethodName(method.elementName(),
                                                     method.typeName().name(), true, alternateNames);
                assert (Objects.nonNull(alternateNames.get()));
                final String currentAttrName = beanAttributeName;
                Optional<String> alternateName = alternateNames.get().orElse(List.of()).stream()
                        .filter(it -> !it.equals(currentAttrName))
                        .findFirst();
                if (alternateName.isPresent() && !ctx.map.containsKey(alternateName.get())
                        && !BeanUtils.isReservedWord(alternateName.get())) {
                    beanAttributeName = alternateName.get();
                    existing = ctx.map.get(beanAttributeName);
                }
            }

            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(ctx.map.put(beanAttributeName, method));
                int pos = ctx.allAttributeNames.indexOf(beanAttributeName);
                if (pos >= 0) {
                    ctx.allTypeInfos.set(pos, method);
                }
                continue;
            }

            Object prev = ctx.map.put(beanAttributeName, method);
            assert (Objects.isNull(prev));

            ctx.allTypeInfos.add(method);
            if (ctx.allAttributeNames.contains(beanAttributeName)) {
                throw new IllegalStateException("duplicate attribute name: " + beanAttributeName + " processing " + typeInfo);
            }
            ctx.allAttributeNames.add(beanAttributeName);
        }
    }

    private static void populateMap(Map<String, TypedElementName> map,
                                    TypeInfo typeInfo,
                                    boolean isBeanStyleRequired) {
        if (typeInfo.superTypeInfo().isPresent()) {
            populateMap(map, typeInfo.superTypeInfo().get(), isBeanStyleRequired);
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            TypedElementName existing = map.get(beanAttributeName);
            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(map.put(beanAttributeName, method));
            } else {
                Object prev = map.put(beanAttributeName, method);
                assert (Objects.isNull(prev));
            }
        }
    }

    private boolean determineIfHasAnyClashingMethodNames() {
        return allAttributeNames().stream().anyMatch(this::isBuilderClashingMethodName);
    }

    private boolean isBuilderClashingMethodName(String beanAttributeName) {
        return beanAttributeName.equals("identity")
                || beanAttributeName.equals("get")
                || beanAttributeName.equals("toStringInner");
    }

    private boolean hasBuilder(Optional<TypeInfo> typeInfo, AnnotationAndValue builderTriggerAnnotation) {
        if (typeInfo.isEmpty()) {
            return false;
        }

        TypeName builderAnnoTypeName = builderTriggerAnnotation.typeName();
        boolean hasBuilder = typeInfo.get().annotations().stream()
                .map(AnnotationAndValue::typeName)
                .anyMatch(it -> it.equals(builderAnnoTypeName));
        return hasBuilder || hasBuilder(typeInfo.get().superTypeInfo(), builderTriggerAnnotation);
    }

}
