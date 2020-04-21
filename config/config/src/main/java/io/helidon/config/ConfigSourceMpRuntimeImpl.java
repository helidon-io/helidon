/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import io.helidon.config.spi.ConfigNode;

import org.eclipse.microprofile.config.spi.ConfigSource;

import static io.helidon.config.AbstractConfigImpl.LOGGER;

class ConfigSourceMpRuntimeImpl extends ConfigSourceRuntimeBase {
    private final ConfigSource source;

    ConfigSourceMpRuntimeImpl(ConfigSource source) {
        this.source = source;
    }

    @Override
    public boolean isLazy() {
        // MP config sources are considered eager
        return false;
    }

    @Override
    public void onChange(BiConsumer<String, ConfigNode> change) {
        try {
            // this is not a documented feature
            // it is to enable MP config sources to be "mutable" in Helidon
            // this requires some design decisions (and clarification of the MP Config Specification), as this
            // is open to different interpretations for now
            Method method = source.getClass().getMethod("registerChangeListener", BiConsumer.class);
            BiConsumer<String, String> mpListener = (key, value) -> change.accept(key, ConfigNode.ValueNode.create(value));

            method.invoke(source, mpListener);
        } catch (NoSuchMethodException e) {
            LOGGER.finest("No registerChangeListener(BiConsumer) method found on " + source.getClass() + ", change"
                                  + " support not enabled for this config source (" + source.getName() + ")");
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.WARNING, "Cannot invoke registerChangeListener(BiConsumer) method on " + source.getClass() + ", "
                    + "change support not enabled for this config source ("
                    + source.getName() + ")", e);
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.WARNING, "Invocation of registerChangeListener(BiConsumer) method on " + source.getClass()
                    + " failed with an exception, "
                    + "change support not enabled for this config source ("
                    + source.getName() + ")", e);
        }
    }

    @Override
    public Optional<ConfigNode.ObjectNode> load() {
        return Optional.of(ConfigUtils.mapToObjectNode(source.getProperties(), false));
    }

    @Override
    public Optional<ConfigNode> node(String key) {
        String value = source.getValue(key);

        if (null == value) {
            return Optional.empty();
        }

        return Optional.of(ConfigNode.ValueNode.create(value));
    }

    @Override
    public ConfigSource asMpSource() {
        return source;
    }

    @Override
    public String description() {
        return source.getName();
    }

    @Override
    boolean changesSupported() {
        // supported through a known method signature
        return true;
    }
}
