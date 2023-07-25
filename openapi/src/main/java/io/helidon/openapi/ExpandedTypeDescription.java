/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Extension of {@link TypeDescription} that handles:
 * <ul>
 *     <li>nested enums,</li>
 *     <li>extensible types,</li>
 *     <li>references, and</li>
 *     <li>additional properties (which can be either Boolean or Schema).</li>
 * </ul>
 * <p>
 *     The OpenAPI document format uses lower-case enum names and values, while the SmallRye
 *     definitions use upper-case. This class simplifies adding the special handling for enums
 *     declared within a particular class.
 * </p>
 * <p>
 *     Some of the MP OpenAPI items are extensible, meaning they accept sub-item keys with the
 *     "x-" prefix. This class supports extensions. For scalars it delegates to the normal
 *     SnakeYAML processing to correctly type and parse the scalar. For sequences it
 *     creates {@code List}s. For mappings it creates {@code Map}s. The subnodes of the lists and
 *     maps are handled by the normal SnakeYAML parsing, so the resulting elements in lists and
 *     maps are of the SnakeYAML-inferred types.
 * </p>
 * <p>
 *     A subnode {@code $ref} maps to the {@code ref} property on the MP OpenAPI types. This type
 *     description simplifies defining the {@code $ref} property to those types that support it.
 * </p>
 * <p>
 *     In schemas, the {@code additionalProperties} value can be either a boolean or a schema. The MicroProfile
 *     {@link org.eclipse.microprofile.openapi.models.media.Schema} type exposes {@code getAdditionalPropertiesBoolean},
 *     {@code setAdditionalPropertiesBoolean}, {@code getAdditionalPropertiesSchema}, and {@code setAdditionalPropertiesSchema}
 *     methods. We do not know until runtime and the value is available for each {@code additionalProperties} instance which
 *     type (Boolean or Schema) to use, so we cannot just prepare a smart SnakeYAML {@code Property} implementation. Instead
 *     we augment the schema-specific {@code TypeDescription} so it knows how to decide, at runtime, what to do.
 * </p>
 * <p>
 *     We use this expanded version of {@code TypeDescription} with the generated SnakeYAMLParserHelper class.
 * </p>
 */
class ExpandedTypeDescription extends TypeDescription {

    private static final String EXTENSION_PROPERTY_PREFIX = "x-";

    static final PropertyUtils PROPERTY_UTILS = new PropertyUtils();

    private final Class<?> impl;

    /**
     * Factory method for ease of chaining other method invocations.
     *
     * @param clazz interface type to describe
     * @param impl implementation class for the interface
     * @return resulting TypeDescription
     */
    static ExpandedTypeDescription create(Class<?> clazz, Class<?> impl) {

        ExpandedTypeDescription result;
        if (clazz.equals(Schema.class)) {
            result = new SchemaTypeDescription(clazz, impl);
        } else if (CustomConstructor.CHILD_MAP_TYPES.containsKey(clazz)) {
            CustomConstructor.ChildMapType<?, ?> childMapType = CustomConstructor.CHILD_MAP_TYPES.get(clazz);
            result = childMapType.typeDescriptionFactory().apply(impl);
        } else if (CustomConstructor.CHILD_MAP_OF_LIST_TYPES.containsKey(clazz)) {
            CustomConstructor.ChildMapListType<?, ?> childMapListType = CustomConstructor.CHILD_MAP_OF_LIST_TYPES.get(clazz);
            result = childMapListType.typeDescriptionFunction().apply(impl);
        } else {
            result = new ExpandedTypeDescription(clazz, impl);
        }
        result.setPropertyUtils(PROPERTY_UTILS);
        return result;
    }

    private ExpandedTypeDescription(Class<?> clazz, Class<?> impl) {
        super(clazz, null, impl);
        this.impl = impl;
    }

    /**
     * Adds property handling for a {@code $ref} reference.
     *
     * @return this type description
     */
    ExpandedTypeDescription addRef() {
        PropertySubstitute sub = new PropertySubstitute("ref", String.class, "getRef", "setRef");
        sub.setTargetType(impl);
        substituteProperty(sub);
        return this;
    }

    /**
     * Adds property handling for extensions.
     *
     * @return this type description
     */
    ExpandedTypeDescription addExtensions() {
        PropertySubstitute sub = new PropertySubstitute("extensions", Map.class, "getExtensions", "setExtensions");
        sub.setTargetType(impl);
        substituteProperty(sub);
        return this;
    }

    @Override
    public Property getProperty(String name) {
        if (isExtension(name)) {
            return new ExtensionProperty(name);
        }
        if (isRef(name)) {
            return new RenamedProperty(this.getType(), "ref");
        }
        return super.getProperty(name);
    }

    Property getPropertyNoEx(String name) {
        try {
            Property p = getProperty("defaultValue");
            return p;
        } catch (YAMLException ex) {
            if (ex.getMessage().startsWith("Unable to find property")) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public Object newInstance(String propertyName, Node node) {
        Property p = getProperty(propertyName);
        if (p.getType().isEnum()) {
            @SuppressWarnings("unchecked")
            Class<Enum> eClass = (Class<Enum>) p.getType();
            String valueText = ScalarNode.class.cast(node).getValue();
            for (Enum e : eClass.getEnumConstants()) {
                if (e.toString().equals(valueText)) {
                    return e;
                }
            }
        }
        return super.newInstance(propertyName, node);
    }

    @Override
    public boolean setupPropertyType(String key, Node valueNode) {
        return setupExtensionType(key, valueNode) || super.setupPropertyType(key, valueNode);
    }

    void addExcludes(String... propNames) {
        if (excludes == Collections.<String>emptySet()) {
            excludes = new HashSet<String>();
        }
        Collections.addAll(excludes, propNames);
    }

    /**
     * Returns the implementation class associated with this type descr.
     *
     * @return implementation class
     */
    Class<?> impl() {
        return impl;
    }

    /**
     *
     * @return the 'default' property for this type; null if none
     */
    Property defaultProperty() {
        return getPropertyNoEx("defaultValue");
    }

    private static boolean setupExtensionType(String key, Node valueNode) {
        if (isExtension(key)) {
            /*
             * The nodeId in a node is more like node "category" in SnakeYAML. For those OpenAPI interfaces which implement
             * Extensible we need to set the node's type if the extension is a List or Map.
             */
            switch (valueNode.getNodeId()) {
                case sequence:
                    valueNode.setType(List.class);
                    return true;

                case anchor:
                    break;

                case mapping:
                    valueNode.setType(Map.class);
                    return true;

                case scalar:
                    break;

                default:

            }
        }
        return false;
    }

    private static boolean isExtension(String name) {
        return name.startsWith(EXTENSION_PROPERTY_PREFIX);
    }

    private static boolean isRef(String name) {
        return name.equals("$ref");
    }

    /**
     * Specific type description for {@code Schema}.
     * <p>
     *     The {@code Schema} node allows the {@code additionalProperties} subnode to be either
     *     {@code Boolean} or another {@code Schema}, and the {@code Schema} class exposes getters and setters for
     *     {@code additionalPropertiesBoolean}, and {@code additionalPropertiesSchema}.
     *     This type description customizes the handling of {@code additionalProperties} to account for all that.
     * </p>
     * @see io.helidon.openapi.Serializer (specifically doRepresentJavaBeanProperty) for output handling for additionalProperties
     */
    static final class SchemaTypeDescription extends ExpandedTypeDescription {

        private static final PropertyDescriptor ADDL_PROPS_PROP_DESCRIPTOR = preparePropertyDescriptor();

        private static final Property ADDL_PROPS_PROPERTY =
                new MethodProperty(ADDL_PROPS_PROP_DESCRIPTOR) {

                    @Override
                    public void set(Object object, Object value) throws Exception {
                        Schema s = Schema.class.cast(object);
                        if (value instanceof Schema) {
                            s.setAdditionalPropertiesSchema((Schema) value);
                        } else {
                            s.setAdditionalPropertiesBoolean((Boolean) value);
                        }
                    }

                    @Override
                    public Object get(Object object) {
                        Schema s = Schema.class.cast(object);
                        Boolean b = s.getAdditionalPropertiesBoolean();
                        return b != null ? b : s.getAdditionalPropertiesSchema();
                    }
                };

        private static PropertyDescriptor preparePropertyDescriptor() {
            /*
             * The PropertyDescriptor here is just a placeholder. We will not know until we are mapping a node in the model
             * whether the additionalProperties is a boolean or a Schema. That is handled explicitly in setProperty below.
             */
            try {
                return new PropertyDescriptor("additionalProperties",
                                              Schema.class.getMethod("getAdditionalPropertiesSchema"),
                                              Schema.class.getMethod("setAdditionalPropertiesSchema", Schema.class));
            } catch (IntrospectionException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean setupPropertyType(String key, Node valueNode) {
            if (key.equals("additionalProperties")) {
                valueNode.setType(valueNode.getTag().equals(Tag.BOOL) ? Boolean.class : Schema.class);
                return true;
            }
            return super.setupPropertyType(key, valueNode);
        }

        @Override
        public Property getProperty(String name) {
            return name.equals("additionalProperties") ? ADDL_PROPS_PROPERTY : super.getProperty(name);
        }

        @Override
        public boolean setProperty(Object targetBean, String propertyName, Object value) throws Exception {
            if (!(targetBean instanceof Schema schema) || !propertyName.equals("additionalProperties")) {
                return super.setProperty(targetBean, propertyName, value);
            }
            if (value instanceof Boolean) {
                schema.setAdditionalPropertiesBoolean((Boolean) value);
            } else if (value instanceof Schema) {
                schema.setAdditionalPropertiesSchema((Schema) value);
            } else {
                throw new IllegalArgumentException("Expected additionalProperties as Boolean or Schema but was "
                                                           + value.getClass().getName());
            }
            return true;
        }

        private SchemaTypeDescription(Class<?> clazz, Class<?> impl) {
            super(clazz, impl);
        }
    }

    /**
     * Type description for parent/child model objects which resemble maps.
     *
     * @param <P> parent type
     * @param <C> child type
     */
    static class MapLikeTypeDescription<P, C> extends ExpandedTypeDescription {

        private final Class<P> parentType;
        private final Class<C> childType;
        private final CustomConstructor.ChildAdder<P, C> childAdder;

        static <P, C> MapLikeTypeDescription<P, C> create(Class<P> parentType,
                                                          Class<?> impl,
                                                          Class<C> childType,
                                                          CustomConstructor.ChildAdder<P, C> childAdder) {
            return new MapLikeTypeDescription<>(parentType, impl, childType, childAdder);
        }

        MapLikeTypeDescription(Class<P> parentType,
                               Class<?> impl,
                               Class<C> childType,
                               CustomConstructor.ChildAdder<P, C> childAdder) {
            super(parentType, impl);
            this.childType = childType;
            this.parentType = parentType;
            this.childAdder = childAdder;
        }

        @Override
        public boolean setProperty(Object targetBean, String propertyName, Object value) throws Exception {
            P parent = parentType.cast(targetBean);
            C child = childType.cast(value);
            childAdder.addChild(parent, propertyName, child);
            return true;
        }

        protected Class<P> parentType() {
            return parentType;
        }

        protected Class<C> childType() {
            return childType;
        }

        protected CustomConstructor.ChildAdder<P, C> childAdder() {
            return childAdder;
        }
    }

    static class ListMapLikeTypeDescription<P, C> extends MapLikeTypeDescription<P, C> {

        private final CustomConstructor.ChildNameAdder<P> childNameAdder;
        private final CustomConstructor.ChildListAdder<P, C> childListAdder;

        static <P, C> ListMapLikeTypeDescription<P, C> create(Class<P> parentType,
                                                              Class<?> impl,
                                                              Class<C> childType,
                                                              CustomConstructor.ChildAdder<P, C> childAdder,
                                                              CustomConstructor.ChildNameAdder<P> childNameAdder,
                                                              CustomConstructor.ChildListAdder<P, C> childListAdder) {
            return new ListMapLikeTypeDescription<>(parentType, impl, childType, childAdder, childNameAdder, childListAdder);
        }

        private ListMapLikeTypeDescription(Class<P> parentType,
                                   Class<?> impl,
                                   Class<C> childType,
                                   CustomConstructor.ChildAdder<P, C> childAdder,
                                   CustomConstructor.ChildNameAdder<P> childNameAdder,
                                   CustomConstructor.ChildListAdder<P, C> childListAdder) {
            super(parentType, impl, childType, childAdder);
            this.childNameAdder = childNameAdder;
            this.childListAdder = childListAdder;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean setProperty(Object targetBean, String propertyName, Object value) throws Exception {
            P parent = parentType().cast(targetBean);
            if (value == null) {
                childNameAdder.addChild(parent, propertyName);
            } else if (value instanceof List) {
                childListAdder.addChildren(parent, propertyName, (List<C>) value);
            } else {
                childAdder().addChild(parent, propertyName, childType().cast(value));
            }
            return true;
        }
    }

    /**
     * Property description for an extension subnode.
     */
    static class ExtensionProperty extends Property {

        private static final Class[] EXTENSION_TYPE_ARGS = new Class[0];

        ExtensionProperty(String name) {
            super(name, Object.class);
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            /*
             * Extension properties have no type arguments, so we can safely always return an empty array.
             */
            return EXTENSION_TYPE_ARGS;
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            asExt(object).addExtension(getName(), value);
        }

        @Override
        public Object get(Object object) {
            return asExt(object).getExtensions().get(getName());
        }

        @Override
        public List<Annotation> getAnnotations() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        private Extensible<?> asExt(Object object) {
            if (!(object instanceof Extensible<?>)) {
                throw new IllegalArgumentException(String.format(
                        "Cannot assign extension %s to object of type %s that does not implement %s", getName(),
                        object.getClass().getName(), Extensible.class.getName()));
            }
            return (Extensible<?>) object;
        }
    }

    /**
     * Specialized property with a different name in the YAML vs. the POJO.
     */
    static class RenamedProperty extends MethodProperty {

        RenamedProperty(Class<?> c, String pojoName) {
            super(propertyDescriptor(c, pojoName));
        }

        private static PropertyDescriptor propertyDescriptor(Class<?> c, String pojoName) {
            try {
                return new PropertyDescriptor("ref", c, "getRef", "setRef");
            } catch (IntrospectionException e) {
                throw new YAMLException("Error describing property " + pojoName + " for class " + c.getName());
            }
        }
    }
}
