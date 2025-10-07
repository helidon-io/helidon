/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.openapi;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.Reference;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.MethodProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

/**
 * Specialized SnakeYAML implementation of {@link org.yaml.snakeyaml.introspector.PropertyUtils} that essentially adds ad hoc
 * properties to the SnakeYAML type if the underlying type meets certain criteria.
 */
class ExtendedPropertyUtils extends PropertyUtils {

    private static final Map<Class<?>, InferredProperty> TYPE_INFO = Map.of(
            Extensible.class, InferredProperty.create("extensions"),
            Reference.class, InferredProperty.create("ref"));

    @Override
    public Set<Property> getProperties(Class<?> type, BeanAccess bAccess) {
        var result = super.getProperties(type, bAccess);
        TYPE_INFO.forEach((t, info) -> {
            if (t.isAssignableFrom(type)) {
                result.add(info.property(type));
            }
        });
        return result;
    }

    @Override
    protected Map<String, Property> getPropertiesMap(Class<?> type, BeanAccess bAccess) {
        var result = super.getPropertiesMap(type, bAccess);
        TYPE_INFO.forEach((t, info) -> {
            if (t.isAssignableFrom(type)) {
                result.put(info.propertyName, info.property(type));
            }
        });
        return result;
    }

    private record InferredProperty(String propertyName) {

        static InferredProperty create(String propertyName) {
            return new InferredProperty(propertyName);
        }

        Property property(Class<?> type) {
            String capitalizedPropertyName = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            try {
                return new MethodProperty(new PropertyDescriptor(propertyName,
                                                                 type,
                                                                 "get" + capitalizedPropertyName,
                                                                 "set" + capitalizedPropertyName));
            } catch (IntrospectionException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
