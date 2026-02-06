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

package io.helidon.tracing.exporter.jaeger;

import io.helidon.tracing.exporter.jaeger.proto.api_v2.TimeOrBuilder;

import io.opentelemetry.exporter.internal.marshal.ProtoFieldInfo;

@SuppressWarnings("all")
final class Time extends
                 com.google.protobuf.GeneratedMessageV3 implements
                                                        // @@protoc_insertion_point(message_implements:io.opentelemetry.internal.Time)
                                                                TimeOrBuilder {

    static final ProtoFieldInfo SECONDS = ProtoFieldInfo.create(1, 8, "seconds");

    static final ProtoFieldInfo NANOS = ProtoFieldInfo.create(2, 16, "nanos");

    private static final long serialVersionUID = 0L;
    // Use Time.newBuilder() to construct.
    private Time(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
        super(builder);
    }
    private Time() {
    }

    @Override
    @SuppressWarnings({"unused"})
    protected Object newInstance(
            UnusedPrivateParameter unused) {
        return new Time();
    }

    public static final com.google.protobuf.Descriptors.Descriptor
    getDescriptor() {
        return TimeProto.TIME_DESCRIPTOR;
    }

    @Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
    internalGetFieldAccessorTable() {
        return TimeProto.FIELD_ACCESSOR_TABLE
                .ensureFieldAccessorsInitialized(
                        Time.class, Builder.class);
    }

    public static final int SECONDS_FIELD_NUMBER = 1;
    private long seconds_ = 0L;
    /**
     * <pre>
     * Represents seconds of UTC time since Unix epoch
     * 1970-01-01T00:00:00Z. Must be from 0001-01-01T00:00:00Z to
     * 9999-12-31T23:59:59Z inclusive.
     * </pre>
     *
     * <code>int64 seconds = 1;</code>
     * @return The seconds.
     */
    @Override
    public long getSeconds() {
        return seconds_;
    }

    public static final int NANOS_FIELD_NUMBER = 2;
    private int nanos_ = 0;
    /**
     * <pre>
     * Non-negative fractions of a second at nanosecond resolution. Negative
     * second values with fractions must still have non-negative nanos values
     * that count forward in time. Must be from 0 to 999,999,999
     * inclusive.
     * </pre>
     *
     * <code>int32 nanos = 2;</code>
     * @return The nanos.
     */
    @Override
    public int getNanos() {
        return nanos_;
    }

    private byte memoizedIsInitialized = -1;
    @Override
    public final boolean isInitialized() {
        byte isInitialized = memoizedIsInitialized;
        if (isInitialized == 1) return true;
        if (isInitialized == 0) return false;

        memoizedIsInitialized = 1;
        return true;
    }

    @Override
    public void writeTo(com.google.protobuf.CodedOutputStream output)
            throws java.io.IOException {
        if (seconds_ != 0L) {
            output.writeInt64(1, seconds_);
        }
        if (nanos_ != 0) {
            output.writeInt32(2, nanos_);
        }
        getUnknownFields().writeTo(output);
    }

    @Override
    public int getSerializedSize() {
        int size = memoizedSize;
        if (size != -1) return size;

        size = 0;
        if (seconds_ != 0L) {
            size += com.google.protobuf.CodedOutputStream
                    .computeInt64Size(1, seconds_);
        }
        if (nanos_ != 0) {
            size += com.google.protobuf.CodedOutputStream
                    .computeInt32Size(2, nanos_);
        }
        size += getUnknownFields().getSerializedSize();
        memoizedSize = size;
        return size;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Time)) {
            return super.equals(obj);
        }
        Time other = (Time) obj;

        if (getSeconds()
                != other.getSeconds()) return false;
        if (getNanos()
                != other.getNanos()) return false;
        if (!getUnknownFields().equals(other.getUnknownFields())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        if (memoizedHashCode != 0) {
            return memoizedHashCode;
        }
        int hash = 41;
        hash = (19 * hash) + getDescriptor().hashCode();
        hash = (37 * hash) + SECONDS_FIELD_NUMBER;
        hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
                getSeconds());
        hash = (37 * hash) + NANOS_FIELD_NUMBER;
        hash = (53 * hash) + getNanos();
        hash = (29 * hash) + getUnknownFields().hashCode();
        memoizedHashCode = hash;
        return hash;
    }

    public static Time parseFrom(
            java.nio.ByteBuffer data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }
    public static Time parseFrom(
            java.nio.ByteBuffer data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Time parseFrom(
            com.google.protobuf.ByteString data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }
    public static Time parseFrom(
            com.google.protobuf.ByteString data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Time parseFrom(byte[] data)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data);
    }
    public static Time parseFrom(
            byte[] data,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
        return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Time parseFrom(java.io.InputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }
    public static Time parseFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public static Time parseDelimitedFrom(java.io.InputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input);
    }

    public static Time parseDelimitedFrom(
            java.io.InputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static Time parseFrom(
            com.google.protobuf.CodedInputStream input)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input);
    }
    public static Time parseFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws java.io.IOException {
        return com.google.protobuf.GeneratedMessageV3
                .parseWithIOException(PARSER, input, extensionRegistry);
    }

    @Override
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
        return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(Time prototype) {
        return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    @Override
    public Builder toBuilder() {
        return this == DEFAULT_INSTANCE
                ? new Builder() : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(
            com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        Builder builder = new Builder(parent);
        return builder;
    }
    /**
     * <pre>
     * Copied from google.protobuf.Timestamp to provide access to the wire format.
     * </pre>
     *
     * Protobuf type {@code io.opentelemetry.internal.Time}
     */
    public static final class Builder extends
                                      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
                                                                                              // @@protoc_insertion_point(builder_implements:io.opentelemetry.internal.Time)
                                                                                                      TimeOrBuilder {
        public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
            return TimeProto.TIME_DESCRIPTOR;
        }

        @Override
        protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
            return TimeProto.FIELD_ACCESSOR_TABLE
                    .ensureFieldAccessorsInitialized(
                            Time.class, Builder.class);
        }

        // Construct using Time.newBuilder()
        private Builder() {

        }

        private Builder(
                com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
            super(parent);

        }
        @Override
        public Builder clear() {
            super.clear();
            bitField0_ = 0;
            seconds_ = 0L;
            _nanos = 0;
            return this;
        }

        @Override
        public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
            return TimeProto.TIME_DESCRIPTOR;
        }

        @Override
        public Time getDefaultInstanceForType() {
            return Time.getDefaultInstance();
        }

        @Override
        public Time build() {
            Time result = buildPartial();
            if (!result.isInitialized()) {
                throw newUninitializedMessageException(result);
            }
            return result;
        }

        @Override
        public Time buildPartial() {
            Time result = new Time(this);
            if (bitField0_ != 0) { buildPartial0(result); }
            onBuilt();
            return result;
        }

        private void buildPartial0(Time result) {
            int from_bitField0_ = bitField0_;
            if (((from_bitField0_ & 0x00000001) != 0)) {
                result.seconds_ = seconds_;
            }
            if (((from_bitField0_ & 0x00000002) != 0)) {
                result.nanos_ = _nanos;
            }
        }

        @Override
        public Builder clone() {
            return super.clone();
        }
        @Override
        public Builder setField(
                com.google.protobuf.Descriptors.FieldDescriptor field,
                Object value) {
            return super.setField(field, value);
        }
        @Override
        public Builder clearField(
                com.google.protobuf.Descriptors.FieldDescriptor field) {
            return super.clearField(field);
        }
        @Override
        public Builder clearOneof(
                com.google.protobuf.Descriptors.OneofDescriptor oneof) {
            return super.clearOneof(oneof);
        }
        @Override
        public Builder setRepeatedField(
                com.google.protobuf.Descriptors.FieldDescriptor field,
                int index, Object value) {
            return super.setRepeatedField(field, index, value);
        }
        @Override
        public Builder addRepeatedField(
                com.google.protobuf.Descriptors.FieldDescriptor field,
                Object value) {
            return super.addRepeatedField(field, value);
        }
        @Override
        public Builder mergeFrom(com.google.protobuf.Message other) {
            if (other instanceof Time) {
                return mergeFrom((Time)other);
            } else {
                super.mergeFrom(other);
                return this;
            }
        }

        public Builder mergeFrom(Time other) {
            if (other == Time.getDefaultInstance()) return this;
            if (other.getSeconds() != 0L) {
                setSeconds(other.getSeconds());
            }
            if (other.getNanos() != 0) {
                setNanos(other.getNanos());
            }
            this.mergeUnknownFields(other.getUnknownFields());
            onChanged();
            return this;
        }

        @Override
        public final boolean isInitialized() {
            return true;
        }

        @Override
        public Builder mergeFrom(
                com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            if (extensionRegistry == null) {
                throw new NullPointerException();
            }
            try {
                boolean done = false;
                while (!done) {
                    int tag = input.readTag();
                    switch (tag) {
                    case 0:
                        done = true;
                        break;
                    case 8: {
                        seconds_ = input.readInt64();
                        bitField0_ |= 0x00000001;
                        break;
                    } // case 8
                    case 16: {
                        _nanos = input.readInt32();
                        bitField0_ |= 0x00000002;
                        break;
                    } // case 16
                    default: {
                        if (!super.parseUnknownField(input, extensionRegistry, tag)) {
                            done = true; // was an endgroup tag
                        }
                        break;
                    } // default:
                    } // switch (tag)
                } // while (!done)
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw e.unwrapIOException();
            } finally {
                onChanged();
            } // finally
            return this;
        }
        private int bitField0_;

        private long seconds_ ;
        /**
         * <pre>
         * Represents seconds of UTC time since Unix epoch
         * 1970-01-01T00:00:00Z. Must be from 0001-01-01T00:00:00Z to
         * 9999-12-31T23:59:59Z inclusive.
         * </pre>
         *
         * <code>int64 seconds = 1;</code>
         * @return The seconds.
         */
        @Override
        public long getSeconds() {
            return seconds_;
        }
        /**
         * <pre>
         * Represents seconds of UTC time since Unix epoch
         * 1970-01-01T00:00:00Z. Must be from 0001-01-01T00:00:00Z to
         * 9999-12-31T23:59:59Z inclusive.
         * </pre>
         *
         * <code>int64 seconds = 1;</code>
         * @param value The seconds to set.
         * @return This builder for chaining.
         */
        public Builder setSeconds(long value) {

            seconds_ = value;
            bitField0_ |= 0x00000001;
            onChanged();
            return this;
        }
        /**
         * <pre>
         * Represents seconds of UTC time since Unix epoch
         * 1970-01-01T00:00:00Z. Must be from 0001-01-01T00:00:00Z to
         * 9999-12-31T23:59:59Z inclusive.
         * </pre>
         *
         * <code>int64 seconds = 1;</code>
         * @return This builder for chaining.
         */
        public Builder clearSeconds() {
            bitField0_ = (bitField0_ & ~0x00000001);
            seconds_ = 0L;
            onChanged();
            return this;
        }

        private int _nanos;
        /**
         * <pre>
         * Non-negative fractions of a second at nanosecond resolution. Negative
         * second values with fractions must still have non-negative nanos values
         * that count forward in time. Must be from 0 to 999,999,999
         * inclusive.
         * </pre>
         *
         * <code>int32 nanos = 2;</code>
         * @return The nanos.
         */
        @Override
        public int getNanos() {
            return _nanos;
        }
        /**
         * <pre>
         * Non-negative fractions of a second at nanosecond resolution. Negative
         * second values with fractions must still have non-negative nanos values
         * that count forward in time. Must be from 0 to 999,999,999
         * inclusive.
         * </pre>
         *
         * <code>int32 nanos = 2;</code>
         * @param value The nanos to set.
         * @return This builder for chaining.
         */
        public Builder setNanos(int value) {

            _nanos = value;
            bitField0_ |= 0x00000002;
            onChanged();
            return this;
        }
        /**
         * <pre>
         * Non-negative fractions of a second at nanosecond resolution. Negative
         * second values with fractions must still have non-negative nanos values
         * that count forward in time. Must be from 0 to 999,999,999
         * inclusive.
         * </pre>
         *
         * <code>int32 nanos = 2;</code>
         * @return This builder for chaining.
         */
        public Builder clearNanos() {
            bitField0_ = (bitField0_ & ~0x00000002);
            _nanos = 0;
            onChanged();
            return this;
        }

        @Override
        public final Builder setUnknownFields(
                final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.setUnknownFields(unknownFields);
        }

        @Override
        public final Builder mergeUnknownFields(
                final com.google.protobuf.UnknownFieldSet unknownFields) {
            return super.mergeUnknownFields(unknownFields);
        }


        // @@protoc_insertion_point(builder_scope:io.opentelemetry.internal.Time)
    }

    // @@protoc_insertion_point(class_scope:io.opentelemetry.internal.Time)
    private static final Time DEFAULT_INSTANCE;
    static {
        DEFAULT_INSTANCE = new Time();
    }

    public static Time getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<Time>
            PARSER = new com.google.protobuf.AbstractParser<Time>() {
        @Override
        public Time parsePartialFrom(
                com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            Builder builder = newBuilder();
            try {
                builder.mergeFrom(input, extensionRegistry);
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw e.setUnfinishedMessage(builder.buildPartial());
            } catch (com.google.protobuf.UninitializedMessageException e) {
                throw e.asInvalidProtocolBufferException().setUnfinishedMessage(builder.buildPartial());
            } catch (java.io.IOException e) {
                throw new com.google.protobuf.InvalidProtocolBufferException(e)
                        .setUnfinishedMessage(builder.buildPartial());
            }
            return builder.buildPartial();
        }
    };

    public static com.google.protobuf.Parser<Time> parser() {
        return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<Time> getParserForType() {
        return PARSER;
    }

    @Override
    public Time getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }
}
