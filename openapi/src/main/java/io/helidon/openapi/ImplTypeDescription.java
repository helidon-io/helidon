/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Set;

import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Version of {@code TypeDescription} used for implementation classes (rather than the corresponding interfaces) which delegate
 * most method invocations to the {@code TypeDescription} for the related interface.
 */
class ImplTypeDescription extends TypeDescription {

    private final ExpandedTypeDescription delegate;

    ImplTypeDescription(ExpandedTypeDescription td) {
        super(td.getType(), td.impl());
        delegate = td;
    }

    @Override
    public Property getProperty(String name) {
        return name.equals("$ref") ? delegate.getProperty("ref") : delegate.getProperty(name);
    }

    @Override
    public Object newInstance(String propertyName, Node node) {
        return delegate.newInstance(propertyName, node);
    }

    @Override
    public boolean setupPropertyType(String key, Node valueNode) {
        return delegate.setupPropertyType(key, valueNode);
    }

    public Class<?> impl() {
        return delegate.impl();
    }

    @Override
    public Tag getTag() {
        return delegate.getTag();
    }

    @Override
    public void setTag(Tag tag) {
        delegate.setTag(tag);
    }

    @Override
    public void setTag(String tag) {
        delegate.setTag(tag);
    }

    @Override
    @Deprecated
    public void putListPropertyType(String property, Class<?> type) {
        delegate.putListPropertyType(property, type);
    }

    @Override
    @Deprecated
    public Class<? extends Object> getListPropertyType(String property) {
        return delegate.getListPropertyType(property);
    }

    @Override
    @Deprecated
    public void putMapPropertyType(String property, Class<?> key, Class<?> value) {
        delegate.putMapPropertyType(property, key, value);
    }

    @Override
    @Deprecated
    public Class<? extends Object> getMapKeyType(String property) {
        return delegate.getMapKeyType(property);
    }

    @Override
    @Deprecated
    public Class<? extends Object> getMapValueType(String property) {
        return delegate.getMapValueType(property);
    }

    @Override
    public void addPropertyParameters(String pName, Class<?>... classes) {
        delegate.addPropertyParameters(pName, classes);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void substituteProperty(String pName, Class<?> pType, String getter, String setter, Class<?>... argParams) {
        delegate.substituteProperty(pName, pType, getter, setter, argParams);
    }

    @Override
    public void substituteProperty(PropertySubstitute substitute) {
        delegate.substituteProperty(substitute);
    }

    @Override
    public void setPropertyUtils(PropertyUtils propertyUtils) {
        delegate.setPropertyUtils(propertyUtils);
    }

    @Override
    public void setIncludes(String... propNames) {
        delegate.setIncludes(propNames);
    }

    @Override
    public void setExcludes(String... propNames) {
        delegate.setExcludes(propNames);
    }

    @Override
    public Set<Property> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public boolean setProperty(Object targetBean, String propertyName, Object value) throws Exception {
        return delegate.setProperty(targetBean, propertyName, value);
    }

    @Override
    public Object newInstance(Node node) {
        return delegate.newInstance(node);
    }

    @Override
    public Object finalizeConstruction(Object obj) {
        return delegate.finalizeConstruction(obj);
    }

    @Override
    public Class<? extends Object> getType() {
        return delegate.impl();
    }
}
