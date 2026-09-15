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

package io.helidon.http.http3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.quic.VariableLengthEncoder;

/**
 * Immutable HTTP/3 SETTINGS values.
 */
@Api.Internal
public final class Http3Settings {
    static final long QPACK_MAX_TABLE_CAPACITY_ID = 0x01;
    static final long MAX_FIELD_SECTION_SIZE_ID = 0x06;
    static final long QPACK_BLOCKED_STREAMS_ID = 0x07;

    private static final Set<Long> RESERVED_IDS = Set.of(0L, 0x02L, 0x03L, 0x04L, 0x05L);

    private final OptionalLong maxFieldSectionSize;
    private final long qpackMaxTableCapacity;
    private final long qpackBlockedStreams;
    private final Map<Long, Long> extensionSettings;

    private Http3Settings(OptionalLong maxFieldSectionSize,
                          long qpackMaxTableCapacity,
                          long qpackBlockedStreams,
                          Map<Long, Long> extensionSettings) {
        this.maxFieldSectionSize = Objects.requireNonNull(maxFieldSectionSize, "maxFieldSectionSize");
        this.qpackMaxTableCapacity = requireVarInt("qpackMaxTableCapacity", qpackMaxTableCapacity);
        this.qpackBlockedStreams = requireVarInt("qpackBlockedStreams", qpackBlockedStreams);
        if (maxFieldSectionSize.isPresent()) {
            requireVarInt("maxFieldSectionSize", maxFieldSectionSize.orElseThrow());
        }

        Objects.requireNonNull(extensionSettings, "extensionSettings");
        Map<Long, Long> validatedExtensions = new LinkedHashMap<>();
        extensionSettings.forEach((id, value) -> {
            long validatedId = requireVarInt("extension setting ID", Objects.requireNonNull(id, "extension setting ID"));
            long validatedValue = requireVarInt("extension setting value",
                                                Objects.requireNonNull(value, "extension setting value"));
            if (isKnownId(validatedId)) {
                throw new IllegalArgumentException("Extension setting ID collides with a known setting: " + validatedId);
            }
            if (isReservedId(validatedId)) {
                throw new IllegalArgumentException("Extension setting ID is reserved: " + validatedId);
            }
            validatedExtensions.put(validatedId, validatedValue);
        });
        this.extensionSettings = Map.copyOf(validatedExtensions);
    }

    /**
     * Create settings without a maximum field-section size.
     *
     * @param qpackMaxTableCapacity QPACK maximum dynamic-table capacity
     * @param qpackBlockedStreams QPACK blocked-stream limit
     * @return settings
     */
    public static Http3Settings create(long qpackMaxTableCapacity, long qpackBlockedStreams) {
        return create(OptionalLong.empty(), qpackMaxTableCapacity, qpackBlockedStreams, Map.of());
    }

    /**
     * Create settings with a maximum field-section size.
     *
     * @param maxFieldSectionSize maximum field-section size
     * @param qpackMaxTableCapacity QPACK maximum dynamic-table capacity
     * @param qpackBlockedStreams QPACK blocked-stream limit
     * @return settings
     */
    public static Http3Settings create(long maxFieldSectionSize,
                                       long qpackMaxTableCapacity,
                                       long qpackBlockedStreams) {
        return create(OptionalLong.of(maxFieldSectionSize), qpackMaxTableCapacity, qpackBlockedStreams, Map.of());
    }

    /**
     * Create settings from configuration values that use {@code -1} as the unconfigured sentinel.
     * <p>
     * An unconfigured maximum field-section size is omitted from the SETTINGS frame. Unconfigured QPACK values use the
     * protocol default of {@code 0}. Every configured value must be between {@code 0} and {@code 2^62 - 1}, inclusive.
     *
     * @param maxFieldSectionSize maximum field-section size, or {@code -1} to omit the setting
     * @param qpackMaxTableCapacity QPACK maximum dynamic-table capacity, or {@code -1} to use {@code 0}
     * @param qpackBlockedStreams QPACK blocked-stream limit, or {@code -1} to use {@code 0}
     * @return settings
     */
    public static Http3Settings createConfigured(long maxFieldSectionSize,
                                                 long qpackMaxTableCapacity,
                                                 long qpackBlockedStreams) {
        OptionalLong configuredMaxFieldSectionSize = maxFieldSectionSize == -1
                ? OptionalLong.empty()
                : OptionalLong.of(requireConfiguredVarInt("maxFieldSectionSize", maxFieldSectionSize));
        long configuredQpackMaxTableCapacity = qpackMaxTableCapacity == -1
                ? 0
                : requireConfiguredVarInt("qpackMaxTableCapacity", qpackMaxTableCapacity);
        long configuredQpackBlockedStreams = qpackBlockedStreams == -1
                ? 0
                : requireConfiguredVarInt("qpackBlockedStreams", qpackBlockedStreams);
        return create(configuredMaxFieldSectionSize,
                      configuredQpackMaxTableCapacity,
                      configuredQpackBlockedStreams,
                      Map.of());
    }

    static Http3Settings create(OptionalLong maxFieldSectionSize,
                                long qpackMaxTableCapacity,
                                long qpackBlockedStreams,
                                Map<Long, Long> extensionSettings) {
        return new Http3Settings(maxFieldSectionSize,
                                 qpackMaxTableCapacity,
                                 qpackBlockedStreams,
                                 extensionSettings);
    }

    static boolean isKnownId(long id) {
        return id == QPACK_MAX_TABLE_CAPACITY_ID
                || id == MAX_FIELD_SECTION_SIZE_ID
                || id == QPACK_BLOCKED_STREAMS_ID;
    }

    static boolean isReservedId(long id) {
        return RESERVED_IDS.contains(id);
    }

    /**
     * Maximum field-section size advertised by the endpoint.
     *
     * @return maximum field-section size, or empty when omitted
     */
    public OptionalLong maxFieldSectionSize() {
        return maxFieldSectionSize;
    }

    /**
     * QPACK maximum dynamic-table capacity.
     *
     * @return QPACK maximum dynamic-table capacity
     */
    public long qpackMaxTableCapacity() {
        return qpackMaxTableCapacity;
    }

    /**
     * QPACK blocked-stream limit.
     *
     * @return QPACK blocked-stream limit
     */
    public long qpackBlockedStreams() {
        return qpackBlockedStreams;
    }

    /**
     * Unknown extension settings retained from the SETTINGS frame.
     *
     * @return immutable extension settings by identifier
     */
    public Map<Long, Long> extensionSettings() {
        return extensionSettings;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Http3Settings other)) {
            return false;
        }
        return qpackMaxTableCapacity == other.qpackMaxTableCapacity
                && qpackBlockedStreams == other.qpackBlockedStreams
                && maxFieldSectionSize.equals(other.maxFieldSectionSize)
                && extensionSettings.equals(other.extensionSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxFieldSectionSize, qpackMaxTableCapacity, qpackBlockedStreams, extensionSettings);
    }

    @Override
    public String toString() {
        return "Http3Settings[maxFieldSectionSize=" + maxFieldSectionSize
                + ", qpackMaxTableCapacity=" + qpackMaxTableCapacity
                + ", qpackBlockedStreams=" + qpackBlockedStreams
                + ", extensionSettings=" + extensionSettings
                + ']';
    }

    private static long requireVarInt(String name, long value) {
        if (value < 0 || value > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException(name + " must be a QUIC variable-length integer: " + value);
        }
        return value;
    }

    private static long requireConfiguredVarInt(String name, long value) {
        if (value < 0 || value > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException(name + " must be -1 or a QUIC variable-length integer: " + value);
        }
        return value;
    }
}
