/*
 * This class is copied from pre-Java 9 H2 src.
 * The only modification is the addition of com.oracle.svm.core annotations to provide substitutions
 * Why?
 * The Java 9 version of this 1.4.199 org.h2.util.Bits class used VarHandle
 * causing "com.oracle.graal.pointsto.constraints.UnsupportedFeatureException: VarHandle object must be a compile time constant"
 * during native image build
 *
 * Helidon changes are under the copyright of:
 *
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
 *
 * Author: Paul Parkinson, Oracle
 */

/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

package io.helidon.dbclient.jdbc.h2;


import java.util.UUID;

/**
 * Manipulations with bytes and arrays. This class can be overridden in
 * multi-release JAR with more efficient implementation for a newer versions of
 * Java.
 */
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

@TargetClass(className = "org.h2.util.Bits")
@Substitute
public final class Bits {
    /*
     * Signatures of methods should match with
     * h2/src/java9/src/org/h2/util/Bits.java and precompiled
     * h2/src/java9/precompiled/org/h2/util/Bits.class.
     */

    private static void printStackTrace(String message) {
        new Exception(message).printStackTrace();
    }
    /**
     * Compare the contents of two char arrays. If the content or length of the
     * first array is smaller than the second array, -1 is returned. If the content
     * or length of the second array is smaller than the first array, 1 is returned.
     * If the contents and lengths are the same, 0 is returned.
     *
     * @param data1
     *            the first char array (must not be null)
     * @param data2
     *            the second char array (must not be null)
     * @return the result of the comparison (-1, 1 or 0)
     */
    @Substitute
    public static int compareNotNull(char[] data1, char[] data2) {
        if (data1 == data2) {
            return 0;
        }
        int len = Math.min(data1.length, data2.length);
        for (int i = 0; i < len; i++) {
            char b = data1[i];
            char b2 = data2[i];
            if (b != b2) {
                return b > b2 ? 1 : -1;
            }
        }
        return Integer.signum(data1.length - data2.length);
    }

    /**
     * Compare the contents of two byte arrays. If the content or length of the
     * first array is smaller than the second array, -1 is returned. If the content
     * or length of the second array is smaller than the first array, 1 is returned.
     * If the contents and lengths are the same, 0 is returned.
     *
     * <p>
     * This method interprets bytes as signed.
     * </p>
     *
     * @param data1
     *            the first byte array (must not be null)
     * @param data2
     *            the second byte array (must not be null)
     * @return the result of the comparison (-1, 1 or 0)
     */
    @Substitute
    public static int compareNotNullSigned(byte[] data1, byte[] data2) {
        printStackTrace("intentional");
        if (data1 == data2) {
            return 0;
        }
        int len = Math.min(data1.length, data2.length);
        for (int i = 0; i < len; i++) {
            byte b = data1[i];
            byte b2 = data2[i];
            if (b != b2) {
                return b > b2 ? 1 : -1;
            }
        }
        return Integer.signum(data1.length - data2.length);
    }

    /**
     * Compare the contents of two byte arrays. If the content or length of the
     * first array is smaller than the second array, -1 is returned. If the content
     * or length of the second array is smaller than the first array, 1 is returned.
     * If the contents and lengths are the same, 0 is returned.
     *
     * <p>
     * This method interprets bytes as unsigned.
     * </p>
     *
     * @param data1
     *            the first byte array (must not be null)
     * @param data2
     *            the second byte array (must not be null)
     * @return the result of the comparison (-1, 1 or 0)
     */
    @Substitute
    public static int compareNotNullUnsigned(byte[] data1, byte[] data2) {
        printStackTrace("intentional");
        if (data1 == data2) {
            return 0;
        }
        int len = Math.min(data1.length, data2.length);
        for (int i = 0; i < len; i++) {
            int b = data1[i] & 0xff;
            int b2 = data2[i] & 0xff;
            if (b != b2) {
                return b > b2 ? 1 : -1;
            }
        }
        return Integer.signum(data1.length - data2.length);
    }

    /**
     * Reads a int value from the byte array at the given position in big-endian
     * order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
    @Substitute
    public static int readInt(byte[] buff, int pos) {
        printStackTrace("intentional");
        return (buff[pos++] << 24) + ((buff[pos++] & 0xff) << 16) + ((buff[pos++] & 0xff) << 8) + (buff[pos] & 0xff);
    }

    /**
     * Reads a int value from the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
    @Substitute
    public static int readIntLE(byte[] buff, int pos) {
        printStackTrace("intentional");
        return (buff[pos++] & 0xff) + ((buff[pos++] & 0xff) << 8) + ((buff[pos++] & 0xff) << 16) + (buff[pos] << 24);
    }

    /**
     * Reads a long value from the byte array at the given position in
     * big-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
    @Substitute
    public static long readLong(byte[] buff, int pos) {
        printStackTrace("intentional");
        printStackTrace("intentional");
        return (((long) readInt(buff, pos)) << 32) + (readInt(buff, pos + 4) & 0xffff_ffffL);
    }

    /**
     * Reads a long value from the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
//    @Substitute
    public static long readLongLE(byte[] buff, int pos) {
        return (readIntLE(buff, pos) & 0xffff_ffffL) + (((long) readIntLE(buff, pos + 4)) << 32);
    }

    /**
     * Reads a double value from the byte array at the given position in
     * big-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
    @Substitute
    public static double readDouble(byte[] buff, int pos) {
        return Double.longBitsToDouble(readLong(buff, pos));
    }

    /**
     * Reads a double value from the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @return the value
     */
    @Substitute
    public static double readDoubleLE(byte[] buff, int pos) {
        return Double.longBitsToDouble(readLongLE(buff, pos));
    }

    /**
     * Converts UUID value to byte array in big-endian order.
     *
     * @param msb
     *            most significant part of UUID
     * @param lsb
     *            least significant part of UUID
     * @return byte array representation
     */
    @Substitute
    public static byte[] uuidToBytes(long msb, long lsb) {
        printStackTrace("intentional");
        byte[] buff = new byte[16];
        for (int i = 0; i < 8; i++) {
            buff[i] = (byte) ((msb >> (8 * (7 - i))) & 0xff);
            buff[8 + i] = (byte) ((lsb >> (8 * (7 - i))) & 0xff);
        }
        return buff;
    }

    /**
     * Converts UUID value to byte array in big-endian order.
     *
     * @param uuid
     *            UUID value
     * @return byte array representation
     */
    @Substitute
    public static byte[] uuidToBytes(UUID uuid) {
        printStackTrace("intentional");
        return uuidToBytes(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /**
     * Writes a int value to the byte array at the given position in big-endian
     * order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
    @Substitute
    public static void writeInt(byte[] buff, int pos, int x) {
        printStackTrace("intentional");
        buff[pos++] = (byte) (x >> 24);
        buff[pos++] = (byte) (x >> 16);
        buff[pos++] = (byte) (x >> 8);
        buff[pos] = (byte) x;
    }

    /**
     * Writes a int value to the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
//    @Substitute
    public static void writeIntLE(byte[] buff, int pos, int x) {
        buff[pos++] = (byte) x;
        buff[pos++] = (byte) (x >> 8);
        buff[pos++] = (byte) (x >> 16);
        buff[pos] = (byte) (x >> 24);
    }

    /**
     * Writes a long value to the byte array at the given position in big-endian
     * order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
    @Substitute
    public static void writeLong(byte[] buff, int pos, long x) {
        printStackTrace("intentional");
        writeInt(buff, pos, (int) (x >> 32));
        writeInt(buff, pos + 4, (int) x);
    }

    /**
     * Writes a long value to the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
//    @Substitute
    public static void writeLongLE(byte[] buff, int pos, long x) {
        writeIntLE(buff, pos, (int) x);
        writeIntLE(buff, pos + 4, (int) (x >> 32));
    }

    /**
     * Writes a double value to the byte array at the given position in
     * big-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
    @Substitute
    public static void writeDouble(byte[] buff, int pos, double x) {
        writeLong(buff, pos, Double.doubleToRawLongBits(x));
    }

    /**
     * Writes a double value to the byte array at the given position in
     * little-endian order.
     *
     * @param buff
     *            the byte array
     * @param pos
     *            the position
     * @param x
     *            the value to write
     */
//    @Substitute
    public static void writeDoubleLE(byte[] buff, int pos, double x) {
        writeLongLE(buff, pos, Double.doubleToRawLongBits(x));
    }

    private Bits() {
    }
}
