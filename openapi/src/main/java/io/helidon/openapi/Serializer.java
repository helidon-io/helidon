/*
 * Copyright (c) 2019-2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.openapi;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Expresses an existing {@code OpenAPI} instance as an OpenAPI document. This implementation uses
 * SnakeYAML to write OpenAPI documents from the SmallRye MP OpenAPI model interfaces and classes
 * while suppressing the tag output (which would indicate the SmallRye classes -- we don't want to
 * suggest that the output can only be read into the SmallRye implementation).
 */
class Serializer {

    private static final DumperOptions YAML_DUMPER_OPTIONS = new DumperOptions();
    private static final DumperOptions JSON_DUMPER_OPTIONS = new DumperOptions();

    private static final Logger LOGGER = Logger.getLogger(Serializer.class.getName());

    private Serializer() {}

    static {
        YAML_DUMPER_OPTIONS.setIndent(2);
        YAML_DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        JSON_DUMPER_OPTIONS.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        JSON_DUMPER_OPTIONS.setPrettyFlow(true);
        JSON_DUMPER_OPTIONS.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        JSON_DUMPER_OPTIONS.setSplitLines(false);
    }

    static void serialize(Map<Class<?>, ExpandedTypeDescription> types, Map<Class<?>, ExpandedTypeDescription> implsToTypes,
            OpenAPI openAPI, OpenApiSerializer.Format fmt,
            Writer writer) {
        if (fmt == OpenApiSerializer.Format.JSON) {
            serialize(types, implsToTypes, openAPI, writer, JSON_DUMPER_OPTIONS, DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        } else {
            serialize(types, implsToTypes, openAPI, writer, YAML_DUMPER_OPTIONS, DumperOptions.ScalarStyle.PLAIN);
        }
    }

    private static void serialize(Map<Class<?>, ExpandedTypeDescription> types,
            Map<Class<?>, ExpandedTypeDescription> implsToTypes, OpenAPI openAPI, Writer writer,
            DumperOptions dumperOptions,
            DumperOptions.ScalarStyle stringStyle) {

        Yaml yaml = new Yaml(new CustomRepresenter(types, implsToTypes, dumperOptions, stringStyle), dumperOptions);
        yaml.dump(openAPI, new TagSuppressingWriter(writer));
    }

    /**
     * Represents the nodes in the OpenAPI document output by:
     * <ul>
     *     <li>adjusting the output of enum names and values to conform to the OpenAPI spec
     *     (lower-case) rather than the SmallRye implementations (upper-case),</li>
     *     <li>promotes the children of the property "extensions" up one level in the output
     *     document as required by the OpenAPI spec, and</li>
     *     <li>format scalar string nodes with double-quotes for JSON but not for YAML.</li>
     * </ul>
     */
    static class CustomRepresenter extends Representer {

        private static final String EXTENSIONS = "extensions";

        private final DumperOptions dumperOptions;
        private final DumperOptions.ScalarStyle stringStyle;

        private final Map<Class<?>, ExpandedTypeDescription> implsToTypes;

        CustomRepresenter(Map<Class<?>, ExpandedTypeDescription> types,
                Map<Class<?>, ExpandedTypeDescription> implsToTypes, DumperOptions dumperOptions,
                DumperOptions.ScalarStyle stringStyle) {
            this.implsToTypes = implsToTypes;
            this.dumperOptions = dumperOptions;
            this.stringStyle = stringStyle;
            types.forEach((type, typeDescription) -> {
                addTypeDescription(new ImplTypeDescription(typeDescription));
            });
        }

        @Override
        protected Node representScalar(Tag tag, String value, DumperOptions.ScalarStyle style) {
            return super.representScalar(tag, value, isExemptedFromQuotes(tag) ? DumperOptions.ScalarStyle.PLAIN : style);
        }

        @Override
        protected Node representSequence(Tag tag, Iterable<?> sequence, DumperOptions.FlowStyle flowStyle) {
            Node result = super.representSequence(tag, sequence, flowStyle);
            representedObjects.clear();
            return result;
        }

        private boolean isExemptedFromQuotes(Tag tag) {
            return tag.equals(Tag.BINARY) || tag.equals(Tag.BOOL) || tag.equals(Tag.FLOAT)
                    || tag.equals(Tag.INT);
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
                Tag customTag) {
            if (propertyValue == null) {
                return null;
            }

            Property p = property;
            Object v = adjustPropertyValue(propertyValue);
            Class<?> type = implsToTypes.get(javaBean.getClass()).getType();
            if (type.isEnum()) {
                p = new DelegatingProperty(property, property.toString());
            }
            if (propertyValue instanceof Enum) {
                Enum e = (Enum) propertyValue;
                v = e.toString();
            }
            NodeTuple result = okToProcess(javaBean, property)
                    ? super.representJavaBeanProperty(javaBean, p, v, customTag) : null;
            return result;
        }

        private Object adjustPropertyValue(Object propertyValue) {
            /* Some MP OpenAPI TCK tests expect an integer-style format, even for BigDecimal types, if the
             * value is an integer. Because the formatting is done in SnakeYAML code based on the type of the value,
             * we need to replace a, for example BigDecimal that happen to be an integer value, with an Integer.
             * See https://github.com/eclipse/microprofile-open-api/issues/412
             */
            if (Number.class.isInstance(propertyValue) && !Boolean.getBoolean("io.helidon.openapi.skipTCKWorkaround")) {
                Number n = (Number) propertyValue;
                float diff = n.floatValue() - n.intValue();
                if (diff == 0) {
                    propertyValue = Integer.valueOf(n.intValue());
                } else if (Math.abs(diff) < 0.1) {
                    LOGGER.warning(String.format("Integer approximation of %f did not match but the difference was only %e",
                            n, diff));
                }
            }
            return propertyValue;
        }

        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            MappingNode result = super.representJavaBean(properties, javaBean);
            processExtensions(result, javaBean);
            representedObjects.clear();
            return result;
        }

        private void processExtensions(MappingNode node, Object javaBean) {
            if (!Extensible.class.isAssignableFrom(javaBean.getClass())) {
                return;
            }

            List<NodeTuple> tuples = new ArrayList<>(node.getValue());

            if (tuples == null) {
                return;
            }
            List<NodeTuple> updatedTuples = new ArrayList<>();
            Extensible<?> ext = (Extensible<?>) javaBean;


            tuples.forEach(tuple -> {
                Node keyNode = tuple.getKeyNode();
                if (keyNode.getTag().equals(Tag.STR)) {
                    String key = ((ScalarNode) keyNode).getValue();
                    if (key.equals(EXTENSIONS)) {
                        Node valueNode = tuple.getValueNode();
                        if (valueNode.getNodeId().equals(NodeId.mapping)) {
                            MappingNode extensions = MappingNode.class.cast(valueNode);
                            updatedTuples.addAll(extensions.getValue());
                        }
                    } else {
                        updatedTuples.add(tuple);
                    }
                } else {
                    updatedTuples.add(tuple);
                }
            });
            node.setValue(updatedTuples);
        }

        /**
         * Some SmallRye implementation classes have properties not supported by the implemented interface, so ignore those or
         * the serialized YAML will contain SmallRye-only properties.
         *
         * @param javaBean the bean being serialized
         * @param property the property being serialized
         * @return true if the property should be processes; false otherwise
         */
        private boolean okToProcess(Object javaBean, Property property) {
            boolean reject = false;
            reject |= Parameter.class.isAssignableFrom(javaBean.getClass()) && property.getName().equals("hidden");
            return !reject;
        }
    }

    /**
     * Suppress the tag output; the resulting document can be read into any MP OpenAPI
     * implementation, not just SmallRye's.
     */
    static class TagSuppressingWriter extends PrintWriter {

        private static final Pattern SMALLRYE_IMPL_TAG_PATTERN =
                Pattern.compile("!!" + Pattern.quote(OpenAPIImpl.class.getPackage().getName()) + ".+$");

        TagSuppressingWriter(Writer out) {
            super(out);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            int effLen = detag(CharBuffer.wrap(cbuf), off, len);
            if (effLen > 0) {
                super.write(cbuf, off, effLen);
            }
        }

        @Override
        public void write(String s, int off, int len) {
            int effLen = detag(s, off, len);
            if (effLen > 0) {
                super.write(s, off, effLen);
            }
        }

        private int detag(CharSequence cs, int off, int len) {
            int result = len;
            Matcher m = SMALLRYE_IMPL_TAG_PATTERN.matcher(cs.subSequence(off, off + len));
            if (m.matches()) {
                result = len - (m.end() - m.start());
            }

            return result;
        }
    }

    private static class DelegatingProperty extends Property {

        private final Property delegate;

        private DelegatingProperty(Property delegate, String name) {
            super(name, delegate.getType());
            this.delegate = delegate;
        }

        @Override
        public Class<?> getType() {
            return delegate.getType();
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            return delegate.getActualTypeArguments();
        }

        @Override
        public String getName() {
            return super.getName();
        }

        @Override
        public String toString() {
            return super.toString();
        }

        @Override
        public int compareTo(Property o) {
            return super.compareTo(o);
        }

        @Override
        public boolean isWritable() {
            return delegate.isWritable();
        }

        @Override
        public boolean isReadable() {
            return delegate.isReadable();
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            delegate.set(object, value);
        }

        @Override
        public Object get(Object object) {
            return delegate.get(object);
        }

        @Override
        public List<Annotation> getAnnotations() {
            return delegate.getAnnotations();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return delegate.getAnnotation(annotationType);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other);
        }
    }

}
