/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.binding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.GenericType;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;

final class JsonBindingImpl implements JsonBinding, JsonBindingConfigurator {

    private static final byte[] NULL_BYTES = "null".getBytes(StandardCharsets.UTF_8);
    private static final char[] NULL_CHARS = "null".toCharArray();
    private static final boolean[] WHITESPACE_CHARS = new boolean[256];

    static {
        // ASCII whitespace
        WHITESPACE_CHARS[0x09] = true; // TAB
        WHITESPACE_CHARS[0x0A] = true; // LF
        WHITESPACE_CHARS[0x0B] = true; // VT
        WHITESPACE_CHARS[0x0C] = true; // FF
        WHITESPACE_CHARS[0x0D] = true; // CR
        WHITESPACE_CHARS[0x20] = true; // SPACE
    }

    private final JsonBindingConfig config;
    private final Map<Class<?>, JsonSerializer<?>> initialIdentitySerializers = new IdentityHashMap<>();
    private final Map<Class<?>, JsonDeserializer<?>> initialIdentityDeserializers = new IdentityHashMap<>();
    private final Map<Type, JsonSerializer<?>> initialSerializers = new HashMap<>();
    private final Map<Type, JsonDeserializer<?>> initialDeserializers = new HashMap<>();
    private final Map<Class<?>, JsonBindingFactory<?>> bindingFactories = new IdentityHashMap<>();

    private final Map<Class<?>, JsonSerializer<?>> runtimeIdentitySerializers = new IdentityHashMap<>();
    private final Map<Class<?>, JsonDeserializer<?>> runtimeIdentityDeserializers = new IdentityHashMap<>();
    private final Map<Type, JsonSerializer<?>> runtimeSerializers = new HashMap<>();
    private final Map<Type, JsonDeserializer<?>> runtimeDeserializers = new HashMap<>();

    private final ReentrantReadWriteLock serializerLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock deserializerLock = new ReentrantReadWriteLock();

    JsonBindingImpl(JsonBindingConfig config) {
        this.config = config;
        // Initialize serializers from config
        // Store by GenericType and by raw Type for lookup flexibility
        for (JsonSerializer<?> serializer : config.serializers()) {
            GenericType<?> type = serializer.type();
            initialSerializers.putIfAbsent(type, serializer);
            initialSerializers.putIfAbsent(type.type(), serializer); // Also store by raw Type
            if (type.isClass()) {
                initialIdentitySerializers.putIfAbsent(type.rawType(), serializer);
            }
        }
        // Initialize deserializers from config
        // Similar dual storage for GenericType and raw Type
        for (JsonDeserializer<?> deserializer : config.deserializers()) {
            GenericType<?> type = deserializer.type();
            initialDeserializers.putIfAbsent(type, deserializer);
            initialDeserializers.putIfAbsent(type.type(), deserializer); // Also store by raw Type
            if (type.isClass()) {
                initialIdentityDeserializers.putIfAbsent(type.rawType(), deserializer);
            }
        }
        // Initialize binding factories from config
        // Factories support multiple types, so register for each supported type
        for (JsonBindingFactory<?> bindingFactory : config.bindingFactories()) {
            bindingFactory.supportedTypes().forEach(type -> bindingFactories.putIfAbsent(type, bindingFactory));
        }
    }

    @Override
    public JsonBindingConfig prototype() {
        return config;
    }

    @Override
    public String serialize(Object obj) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        serialize(outputStream, obj);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public <T> String serialize(T obj, Class<? super T> type) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        serialize(outputStream, obj, type);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public <T> String serialize(T obj, GenericType<? super T> type) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        serialize(outputStream, obj, type);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(OutputStream outputStream, Object obj) {
        if (obj == null) {
            writeNull(outputStream);
            return;
        }
        serialize(outputStream, obj, (JsonSerializer<Object>) serializer(obj.getClass()));
    }

    @Override
    public <T> void serialize(OutputStream outputStream, T obj, Class<? super T> type) {
        if (obj == null) {
            writeNull(outputStream);
            return;
        }
        serialize(outputStream, obj, serializer(type));
    }

    @Override
    public <T> void serialize(OutputStream outputStream, T obj, GenericType<? super T> type) {
        if (obj == null) {
            writeNull(outputStream);
            return;
        }
        serialize(outputStream, obj, serializer(type));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(Writer writer, Object obj) {
        if (obj == null) {
            writeNull(writer);
            return;
        }
        serialize(writer, obj, (JsonSerializer<Object>) serializer(obj.getClass()));
    }

    @Override
    public <T> void serialize(Writer writer, T obj, Class<? super T> type) {
        if (obj == null) {
            writeNull(writer);
            return;
        }
        serialize(writer, obj, serializer(type));
    }

    @Override
    public <T> void serialize(Writer writer, T obj, GenericType<? super T> type) {
        if (obj == null) {
            writeNull(writer);
            return;
        }
        serialize(writer, obj, serializer(type));
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(bytes);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(byte[] bytes, GenericType<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(bytes);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(String jsonStr, Class<T> type) {
        return deserialize(jsonStr.getBytes(StandardCharsets.UTF_8), type);
    }

    @Override
    public <T> T deserialize(String jsonStr, GenericType<T> type) {
        return deserialize(jsonStr.getBytes(StandardCharsets.UTF_8), type);
    }

    @Override
    public <T> T deserialize(InputStream inputStream, Class<T> type) {
        return deserialize(inputStream, 512, type);
    }

    @Override
    public <T> T deserialize(InputStream inputStream, int bufferSize, Class<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(inputStream, bufferSize);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(InputStream inputStream, GenericType<T> type) {
        return deserialize(inputStream, 512, type);
    }

    @Override
    public <T> T deserialize(InputStream inputStream, int bufferSize, GenericType<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(inputStream);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(Reader reader, Class<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(reader);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(Reader reader, GenericType<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(reader);
        if (WHITESPACE_CHARS[parser.currentByte() & 0xff]) {
            parser.nextToken();
        }
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(JsonValue jsonValue, Class<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(jsonValue);
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    public <T> T deserialize(JsonValue jsonValue, GenericType<T> type) {
        JsonDeserializer<T> deserializer = deserializer(type);
        JsonParser parser = JsonParser.create(jsonValue);
        return Deserializers.deserialize(parser, deserializer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonDeserializer<T> deserializer(Type type) {
        return switch (type) {
            case Class<?> clazz -> (JsonDeserializer<T>) deserializer(clazz);
            case GenericType<?> genericType -> (JsonDeserializer<T>) deserializer(genericType);
            case null, default -> deserializer(GenericType.create(type));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonDeserializer<T> deserializer(Class<T> type) {
        JsonDeserializer<T> deserializer = (JsonDeserializer<T>) initialIdentityDeserializers.get(type);
        if (deserializer != null) {
            return deserializer;
        }
        try {
            deserializerLock.readLock().lock();
            deserializer = (JsonDeserializer<T>) runtimeIdentityDeserializers.get(type);
            if (deserializer != null) {
                return deserializer;
            }
        } finally {
            deserializerLock.readLock().unlock();
        }
        try {
            deserializerLock.writeLock().lock();
            JsonBindingFactory<T> factory = (JsonBindingFactory<T>) bindingFactories.get(type);
            if (factory == null) {
                if (type.isArray()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Array.class);
                } else if (type.isEnum()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Enum.class);
                }
                if (factory == null) {
                    throw new IllegalStateException("Deserializer/Converter/BindingFactory for type "
                                                            + type + " is not registered");
                }
            }
            JsonDeserializer<T> factoryDeserializer = factory.createDeserializer(type);
            runtimeDeserializers.putIfAbsent(type, factoryDeserializer);
            runtimeIdentityDeserializers.putIfAbsent(type, factoryDeserializer);
            factoryDeserializer.configure(this);
            return factoryDeserializer;
        } finally {
            deserializerLock.writeLock().unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonDeserializer<T> deserializer(GenericType<T> type) {
        JsonDeserializer<T> deserializer = (JsonDeserializer<T>) initialDeserializers.get(type);
        if (deserializer != null) {
            return deserializer;
        }
        try {
            deserializerLock.readLock().lock();
            deserializer = (JsonDeserializer<T>) runtimeDeserializers.get(type);
            if (deserializer != null) {
                return deserializer;
            }
        } finally {
            deserializerLock.readLock().unlock();
        }
        try {
            deserializerLock.writeLock().lock();
            Class<?> rawType = type.rawType();
            JsonBindingFactory<T> factory = (JsonBindingFactory<T>) bindingFactories.get(rawType);
            if (factory == null) {
                if (rawType.isArray()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Array.class);
                } else if (rawType.isEnum()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Enum.class);
                }
                if (factory == null) {
                    throw new IllegalStateException("Deserializer/Converter/BindingFactory for type "
                                                            + type + " is not registered");
                }
            }
            JsonDeserializer<T> factoryDeserializer = factory.createDeserializer(type);
            runtimeDeserializers.putIfAbsent(type, factoryDeserializer);
            runtimeDeserializers.putIfAbsent(type.type(), factoryDeserializer);
            factoryDeserializer.configure(this);
            if (type.isClass()) {
                runtimeIdentityDeserializers.putIfAbsent(rawType, factoryDeserializer);
            }
            return factoryDeserializer;
        } finally {
            deserializerLock.writeLock().unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonSerializer<T> serializer(Type type) {
        return switch (type) {
            case Class<?> clazz -> (JsonSerializer<T>) serializer(clazz);
            case GenericType<?> genericType -> (JsonSerializer<T>) serializer(genericType);
            case null, default -> serializer(GenericType.create(type));
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonSerializer<T> serializer(Class<T> type) {
        JsonSerializer<T> serializer = (JsonSerializer<T>) initialIdentitySerializers.get(type);
        if (serializer != null) {
            return serializer;
        }
        try {
            serializerLock.readLock().lock();
            serializer = (JsonSerializer<T>) runtimeIdentitySerializers.get(type);
            if (serializer != null) {
                return serializer;
            }
        } finally {
            serializerLock.readLock().unlock();
        }
        try {
            serializerLock.writeLock().lock();
            JsonBindingFactory<T> factory = (JsonBindingFactory<T>) bindingFactories.get(type);
            if (factory == null) {
                if (type.isArray()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Array.class);
                } else if (type.isEnum()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Enum.class);
                } else if (List.class.isAssignableFrom(type)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(List.class);
                } else if (Map.class.isAssignableFrom(type)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Map.class);
                } else if (Set.class.isAssignableFrom(type)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Set.class);
                }
                if (factory == null) {
                    serializer = (JsonSerializer<T>) iterateInterfaces(type);
                    if (serializer == null) {
                        throw new IllegalStateException("Serializer/Converter/BindingFactory for type "
                                                                + type + " is not registered");
                    }
                    runtimeSerializers.putIfAbsent(type, serializer);
                    runtimeIdentitySerializers.putIfAbsent(type, serializer);
                    return serializer;
                }
            }
            JsonSerializer<T> factorySerializer = factory.createSerializer(type);
            runtimeSerializers.putIfAbsent(type, factorySerializer);
            runtimeIdentitySerializers.putIfAbsent(type, factorySerializer);
            factorySerializer.configure(this);
            return factorySerializer;
        } finally {
            serializerLock.writeLock().unlock();
        }
    }

    private JsonSerializer<?> iterateInterfaces(Class<?> type) {
        for (Class<?> anInterface : type.getInterfaces()) {
            JsonSerializer<?> serializer = initialIdentitySerializers.get(anInterface);
            if (serializer != null) {
                return serializer;
            }
            serializer = runtimeIdentitySerializers.get(anInterface);
            if (serializer != null) {
                return serializer;
            }
            Class<?>[] interfaces = anInterface.getInterfaces();
            if (interfaces.length > 0) {
                serializer = iterateInterfaces(anInterface);
            }
            if (serializer != null) {
                return serializer;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> JsonSerializer<T> serializer(GenericType<T> type) {
        JsonSerializer<T> serializer = (JsonSerializer<T>) initialSerializers.get(type);
        if (serializer != null) {
            return serializer;
        }
        try {
            serializerLock.readLock().lock();
            serializer = (JsonSerializer<T>) runtimeSerializers.get(type);
            if (serializer != null) {
                return serializer;
            }
        } finally {
            serializerLock.readLock().unlock();
        }
        try {
            serializerLock.writeLock().lock();
            Class<?> rawType = type.rawType();
            JsonBindingFactory<T> factory = (JsonBindingFactory<T>) bindingFactories.get(rawType);
            if (factory == null) {
                if (rawType.isArray()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Array.class);
                } else if (rawType.isEnum()) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Enum.class);
                } else if (List.class.isAssignableFrom(rawType)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(List.class);
                } else if (Map.class.isAssignableFrom(rawType)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Map.class);
                } else if (Set.class.isAssignableFrom(rawType)) {
                    factory = (JsonBindingFactory<T>) bindingFactories.get(Set.class);
                }
                if (factory == null) {
                    serializer = (JsonSerializer<T>) iterateInterfaces(rawType);
                    if (serializer == null) {
                        throw new IllegalStateException("Serializer/Converter/BindingFactory for type "
                                                                + type + " is not registered");
                    }
                    runtimeSerializers.putIfAbsent(type, serializer);
                    runtimeSerializers.putIfAbsent(type.type(), serializer);
                    if (type.isClass()) {
                        runtimeIdentitySerializers.putIfAbsent(rawType, serializer);
                    }
                    return serializer;
                }
            }
            JsonSerializer<T> factorySerializer = factory.createSerializer(type);
            runtimeSerializers.putIfAbsent(type, factorySerializer);
            runtimeSerializers.putIfAbsent(type.type(), factorySerializer);
            factorySerializer.configure(this);
            if (type.isClass()) {
                runtimeIdentitySerializers.putIfAbsent(rawType, factorySerializer);
            }
            return factorySerializer;
        } finally {
            serializerLock.writeLock().unlock();
        }
    }

    private <T> void serialize(Writer writer, T obj, JsonSerializer<T> serializer) {
        try (JsonGenerator generator = JsonGenerator.create(writer)) {
            serializer.serialize(generator, obj, false);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonBindingException("Failed to serialize an object to writer", e);
        }
    }

    private <T> void serialize(OutputStream stream, T obj, JsonSerializer<T> serializer) {
        try (JsonGenerator generator = JsonGenerator.create(stream)) {
            serializer.serialize(generator, obj, false);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonBindingException("Failed to serialize an object to stream", e);
        }
    }

    private void writeNull(OutputStream outputStream) {
        try {
            outputStream.write(NULL_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write null bytes to JSON output stream.", e);
        }
    }

    private void writeNull(Writer writer) {
        try {
            writer.write(NULL_CHARS);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write null chars to JSON writer.", e);
        }
    }
}
