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

package io.helidon.http.http2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * HTTP settings frame.
 */
public final class Http2Settings implements Http2Frame<Http2Flag.SettingsFlags> {
    private static final System.Logger LOGGER = System.getLogger(Http2Settings.class.getName());
    private final Map<Integer, SettingValue> values;

    Http2Settings(Map<Integer, SettingValue> values) {
        this.values = values;
    }

    /**
     * Create empty settings frame.
     *
     * @return settings frame
     */
    public static Http2Settings create() {
        return new Http2Settings(Map.of());
    }

    /**
     * Settings frame builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Settings frame from frame data.
     *
     * @param frame frame buffer
     * @return settings frame
     */
    public static Http2Settings create(BufferData frame) {
        // a combination of 16bit identifier and 32bits of value
        int available = frame.available();
        if (available % 6 != 0) {
            throw new Http2Exception(Http2ErrorCode.FRAME_SIZE,
                                     "Each setting must be 6 bytes, but frame size is " + available);
        }

        int settingCount = available / 6; // each setting is 6 bytes
        Map<Integer, SettingValue> values = new HashMap<>();

        for (int i = 0; i < settingCount; i++) {
            int identifier = frame.readInt16();

            Http2Setting<?> http2Setting = Http2Setting.BY_ID.get(identifier);
            if (http2Setting != null) {
                values.put(identifier, new SettingValue(http2Setting, http2Setting.read(frame)));
            }
        }
        return new Http2Settings(values);
    }

    private static String toString(String setting, int code, Object value) {
        return String.format("[%s (0x%02x):%s]", setting, code, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Http2FrameData toFrameData(Http2Settings settings, int streamId, Http2Flag.SettingsFlags flags) {
        BufferData data = BufferData.create(values.size() * 6);

        values.values().forEach(it -> {
            Object value = it.value();
            Http2Setting<Object> setting = (Http2Setting<Object>) it.setting();
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, String.format(" - Http2Settings %s:  %s", it.setting().toString(), it.value().toString()));
            }
            setting.write(data, value);
        });
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          frameTypes(),
                                                          flags,
                                                          streamId);

        return new Http2FrameData(header, data);
    }

    @Override
    public String name() {
        return Http2FrameType.SETTINGS.name();
    }

    @Override
    public Http2FrameType frameType() {
        return Http2FrameType.SETTINGS;
    }

    @Override
    public Http2FrameTypes<Http2Flag.SettingsFlags> frameTypes() {
        return Http2FrameTypes.SETTINGS;
    }

    @Override
    public String toString() {
        List<String> settings = new ArrayList<>();
        for (SettingValue value : values.values()) {
            settings.add(toString(value.setting.toString(), value.setting.identifier(), value.value()));
        }

        return String.join("\n", settings);
    }

    /**
     * Value of a setting.
     * Either returns a value defined in these settings, or returns the default value of the setting.
     *
     * @param setting setting
     * @param <T>     type of the setting
     * @return value of the setting
     */
    @SuppressWarnings("unchecked")
    public <T> T value(Http2Setting<T> setting) {
        SettingValue settingValue = values.get(setting.identifier());
        if (settingValue == null) {
            return setting.defaultValue();
        }
        return (T) settingValue.value();
    }

    /**
     * Value of a setting if present in these settings.
     *
     * @param setting setting
     * @param <T>     type of setting
     * @return setting value if present, empty otherwise
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> presentValue(Http2Setting<T> setting) {
        return Optional.ofNullable(values.get(setting.identifier()))
                .map(SettingValue::value)
                .map(it -> (T) it);
    }

    /**
     * Is there a value in these settings for the provided setting.
     *
     * @param setting setting
     * @return whether the setting is present
     */
    public boolean hasValue(Http2Setting<?> setting) {
        return values.containsKey(setting.identifier());
    }

    private record SettingValue(Http2Setting<?> setting, Object value) {
        @Override
        public String toString() {
            return setting + ": " + value;
        }
    }

    /**
     * Fluent API builder for {@link Http2Settings}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, Http2Settings> {
        private final Map<Integer, SettingValue> settings = new HashMap<>();

        private Builder() {
        }

        @Override
        public Http2Settings build() {
            return new Http2Settings(Map.copyOf(settings));
        }

        /**
         * Add a setting to these settings.
         *
         * @param settingType  type of setting
         * @param settingValue value of setting
         * @param <T>          value type
         * @return updated builder
         */
        public <T> Builder add(Http2Setting<T> settingType, T settingValue) {
            settings.put(settingType.identifier(), new SettingValue(settingType, settingValue));
            return this;
        }
    }
}
