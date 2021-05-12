/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.abac.policy.el;

import java.beans.FeatureDescriptor;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.PropertyNotWritableException;

import io.helidon.security.util.AbacSupport;

/**
 * Resolver for {@link AbacSupport} types.
 */
public class AttributeResolver extends ELResolver {

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return getAttributed(base).map(b -> String.class).orElse(null);
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return getAttributed(base).map(attributed -> {
            List<FeatureDescriptor> result = new LinkedList<>();

            Collection<String> attributeNames = attributed.abacAttributeNames();

            for (String name : attributeNames) {
                FeatureDescriptor fd = new FeatureDescriptor();
                fd.setDisplayName(name);
                fd.setName(name);
                result.add(fd);
            }

            return result.iterator();
        }).orElse(null);
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return getAttributed(base)
                .flatMap(attributed -> attributed.abacAttribute(String.valueOf(property)))
                .map(value -> {
                    context.setPropertyResolved(true);
                    return value.getClass();
                })
                .orElse(null);
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        return getAttributed(base)
                .flatMap(attributed -> attributed.abacAttribute(String.valueOf(property)))
                .map(value -> {
                    context.setPropertyResolved(true);
                    return value;
                })
                .orElse(null);
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        throw new PropertyNotWritableException("Cannot write: " + property + ", as security expressions are read-only");
    }

    private Optional<AbacSupport> getAttributed(Object base) {
        if (null == base) {
            return Optional.empty();
        }

        if (base instanceof AbacSupport) {
            return Optional.of((AbacSupport) base);
        }

        return Optional.empty();
    }
}
