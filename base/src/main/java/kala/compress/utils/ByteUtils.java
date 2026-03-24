/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kala.compress.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static java.lang.invoke.MethodHandles.byteArrayViewVarHandle;

/// Utility methods for reading and writing bytes.
///
/// @since 1.14
public final class ByteUtils {

    /// Used to consume bytes.
    ///
    /// @since 1.14
    @FunctionalInterface
    public interface ByteConsumer {
        /// The contract is similar to [OutputStream#write(int)], consume the lower eight bytes of the int as a byte.
        ///
        /// @param b the byte to consume
        /// @throws IOException if consuming fails
        void accept(int b) throws IOException;
    }

    /// Used to supply bytes.
    ///
    /// @since 1.14
    @FunctionalInterface
    public interface ByteSupplier {
        /// The contract is similar to [InputStream#read()], return the byte as an unsigned int, -1 if there are no more bytes.
        ///
        /// @return the supplied byte or -1 if there are no more bytes
        /// @throws IOException if supplying fails
        int getAsByte() throws IOException;
    }

    /// [ByteConsumer] based on [OutputStream].
    ///
    /// @since 1.14
    public static class OutputStreamByteConsumer implements ByteConsumer {
        private final OutputStream os;

        public OutputStreamByteConsumer(final OutputStream os) {
            this.os = os;
        }

        @Override
        public void accept(final int b) throws IOException {
            os.write(b);
        }
    }

    /// Empty array.
    ///
    /// @since 1.21
    public static final byte[] EMPTY_BYTE_ARRAY = {};

    private static void checkReadLength(final int length) {
        if (length > 8) {
            throw new IllegalArgumentException("Can't read more than eight bytes into a long value");
        }
    }

    /// Reads the given byte array as a little-endian long.
    ///
    /// @param bytes the byte array to convert
    /// @return the number read
    public static long fromLittleEndian(final byte[] bytes) {
        return fromLittleEndian(bytes, 0, bytes.length);
    }

    /// Reads the given byte array as a little-endian long.
    ///
    /// @param bytes  the byte array to convert
    /// @param off    the offset into the array that starts the value
    /// @param length the number of bytes representing the value
    /// @return the number read
    /// @throws IllegalArgumentException if len is bigger than eight
    public static long fromLittleEndian(final byte[] bytes, final int off, final int length) {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            l |= (bytes[off + i] & 0xffL) << 8 * i;
        }
        return l;
    }

    /// Reads the given number of bytes from the given supplier as a little-endian long.
    ///
    /// Typically used by our InputStreams that need to count the bytes read as well.
    ///
    ///
    /// @param supplier the supplier for bytes
    /// @param length   the number of bytes representing the value
    /// @return the number read
    /// @throws IllegalArgumentException if len is bigger than eight
    /// @throws IOException              if the supplier fails or doesn't supply the given number of bytes anymore
    public static long fromLittleEndian(final ByteSupplier supplier, final int length) throws IOException {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = supplier.getAsByte();
            if (b == -1) {
                throw new IOException("Premature end of data");
            }
            l |= b << i * 8;
        }
        return l;
    }

    /// Reads the given number of bytes from the given input as little-endian long.
    ///
    /// @param in     the input to read from
    /// @param length the number of bytes representing the value
    /// @return the number read
    /// @throws IllegalArgumentException if len is bigger than eight
    /// @throws IOException              if reading fails or the stream doesn't contain the given number of bytes anymore
    public static long fromLittleEndian(final DataInput in, final int length) throws IOException {
        // somewhat duplicates the ByteSupplier version in order to save the creation of a wrapper object
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = in.readUnsignedByte();
            l |= b << i * 8;
        }
        return l;
    }

    /// Reads the given number of bytes from the given stream as a little-endian long.
    ///
    /// @param in     the stream to read from
    /// @param length the number of bytes representing the value
    /// @return the number read
    /// @throws IllegalArgumentException if len is bigger than eight
    /// @throws IOException              if reading fails or the stream doesn't contain the given number of bytes anymore
    /// @deprecated Unused
    @Deprecated
    public static long fromLittleEndian(final InputStream in, final int length) throws IOException {
        // somewhat duplicates the ByteSupplier version in order to save the creation of a wrapper object
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            final long b = in.read();
            if (b == -1) {
                throw new IOException("Premature end of data");
            }
            l |= b << i * 8;
        }
        return l;
    }

    /// Inserts the given value into the array as a little-endian sequence of the given length starting at the given offset.
    ///
    /// @param b      the array to write into
    /// @param value  the value to insert
    /// @param off    the offset into the array that receives the first byte
    /// @param length the number of bytes to use to represent the value
    public static void toLittleEndian(final byte[] b, final long value, final int off, final int length) {
        long num = value;
        for (int i = 0; i < length; i++) {
            b[off + i] = (byte) (num & 0xff);
            num >>= 8;
        }
    }

    /// Provides the given value to the given consumer as a little-endian sequence of the given length.
    ///
    /// @param consumer the consumer to provide the bytes to
    /// @param value    the value to provide
    /// @param length   the number of bytes to use to represent the value
    /// @throws IOException if writing fails
    public static void toLittleEndian(final ByteConsumer consumer, final long value, final int length) throws IOException {
        long num = value;
        for (int i = 0; i < length; i++) {
            consumer.accept((int) (num & 0xff));
            num >>= 8;
        }
    }

    /// Writes the given value to the given stream as a little-endian array of the given length.
    ///
    /// @param out    the output to write to
    /// @param value  the value to write
    /// @param length the number of bytes to use to represent the value
    /// @throws IOException if writing fails
    /// @deprecated Unused
    @Deprecated
    public static void toLittleEndian(final DataOutput out, final long value, final int length) throws IOException {
        // somewhat duplicates the ByteConsumer version in order to save the creation of a wrapper object
        long num = value;
        for (int i = 0; i < length; i++) {
            out.write((int) (num & 0xff));
            num >>= 8;
        }
    }

    /// Writes the given value to the given stream as a little-endian array of the given length.
    ///
    /// @param out    the stream to write to
    /// @param value  the value to write
    /// @param length the number of bytes to use to represent the value
    /// @throws IOException if writing fails
    public static void toLittleEndian(final OutputStream out, final long value, final int length) throws IOException {
        // somewhat duplicates the ByteConsumer version in order to save the creation of a wrapper object
        long num = value;
        for (int i = 0; i < length; i++) {
            out.write((int) (num & 0xff));
            num >>= 8;
        }
    }

    private static final VarHandle SHORT_LE = byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle SHORT_BE = byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_LE = byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_BE = byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_LE = byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_BE = byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /// Get the byte at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static byte getByte(byte[] array, int offset) {
        return array[offset];
    }

    /// Get the unsigned byte at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static int getUnsignedByte(byte[] array, int offset) {
        return Byte.toUnsignedInt(getByte(array, offset));
    }

    /// Get the little-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static short getShortLE(byte[] array, int offset) {
        return (short) SHORT_LE.get(array, offset);
    }

    /// Get the unsigned little-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static int getUnsignedShortLE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortLE(array, offset));
    }

    /// Get the big-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static short getShortBE(byte[] array, int offset) {
        return (short) SHORT_BE.get(array, offset);
    }

    /// Get the unsigned big-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static int getUnsignedShortBE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortBE(array, offset));
    }

    /// Get the little-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static int getIntLE(byte[] array, int offset) {
        return (int) INT_LE.get(array, offset);
    }

    /// Get the unsigned little-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static long getUnsignedIntLE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntLE(array, offset));
    }

    /// Get the big-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static int getIntBE(byte[] array, int offset) {
        return (int) INT_BE.get(array, offset);
    }

    /// Get the unsigned big-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static long getUnsignedIntBE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntBE(array, offset));
    }

    /// Get the little-endian long at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static long getLongLE(byte[] array, int offset) {
        return (long) LONG_LE.get(array, offset);
    }

    /// Get the big-endian long at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static long getLongBE(byte[] array, int offset) {
        return (long) LONG_BE.get(array, offset);
    }

    // Set

    /// Set the byte at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setByte(byte[] array, int offset, byte value) {
        array[offset] = value;
    }

    /// Set the unsigned byte at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setUnsignedByte(byte[] array, int offset, int value) {
        array[offset] = (byte) (value & 0xff);
    }

    /// Set the little-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setShortLE(byte[] array, int offset, short value) {
        SHORT_LE.set(array, offset, value);
    }

    /// Set the unsigned little-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setUnsignedShortLE(byte[] array, int offset, int value) {
        setShortLE(array, offset, (short) (value & 0xffff));
    }

    /// Set the big-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setShortBE(byte[] array, int offset, short value) {
        SHORT_BE.set(array, offset, value);
    }

    /// Set the unsigned big-endian short at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setUnsignedShortBE(byte[] array, int offset, int value) {
        setShortBE(array, offset, (short) (value & 0xffff));
    }

    /// Set the little-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setIntLE(byte[] array, int offset, int value) {
        INT_LE.set(array, offset, value);
    }

    /// Set the unsigned little-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setUnsignedIntLE(byte[] array, int offset, long value) {
        setIntLE(array, offset, (int) (value & 0xffff_ffffL));
    }

    /// Set the big-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setIntBE(byte[] array, int offset, int value) {
        INT_BE.set(array, offset, value);
    }

    /// Set the unsigned big-endian int at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setUnsignedIntBE(byte[] array, int offset, long value) {
        setIntBE(array, offset, (int) (value & 0xffff_ffffL));
    }

    /// Set the little-endian long at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setLongLE(byte[] array, int offset, long value) {
        LONG_LE.set(array, offset, value);
    }

    /// Set the big-endian long at the given offset.
    ///
    /// @throws ArrayIndexOutOfBoundsException if offset is out of bounds.
    /// @since 1.27.1-2
    public static void setLongBE(byte[] array, int offset, long value) {
        LONG_BE.set(array, offset, value);
    }

    private ByteUtils() {
        /* no instances */
    }
}
