/*
 * Copyright 2026 Glavo
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
package kala.compress.utils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;

/// This interface extends the `ReadableByteChannel` interface and adds methods for reading primitive data types.
///
/// @author Glavo
/// @since 1.27.1-2
public interface DataInputReadableChannel extends ReadableByteChannel {

    /// Returns the byte order of this channel.
    ByteOrder byteOrder();

    /// Reads and returns one input byte.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    byte readByte() throws IOException;

    /// Reads one input byte, zero-extends it to type `int`, and returns the result,
    /// which is therefore in the range `0` through `255`.
    ///
    /// @return the unsigned 8-bit value read.
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    default int readUnsignedByte() throws IOException {
        return Byte.toUnsignedInt(readByte());
    }

    /// Reads two input bytes and returns a `short` value.
    ///
    /// The byte order of this channel is used to interpret the bytes.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    short readShort() throws IOException;

    /// Reads two input bytes and returns an `int` value in the range `0` through `65535`.
    ///
    /// The byte order of this channel is used to interpret the bytes.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    default int readUnsignedShort() throws IOException {
        return Short.toUnsignedInt(readShort());
    }

    /// Reads four input bytes and returns an `int` value.
    ///
    /// The byte order of this channel is used to interpret the bytes.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    int readInt() throws IOException;

    /// Reads four input bytes and returns a `long` value.
    ///
    /// The byte order of this channel is used to interpret the bytes.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    default long readUnsignedInt() throws IOException {
        return Integer.toUnsignedLong(readInt());
    }

    /// Reads eight input bytes and returns a `long` value.
    ///
    /// The byte order of this channel is used to interpret the bytes.
    ///
    /// @throws EOFException if this stream reaches the end before reading all the bytes.
    /// @throws IOException  if an I/O error occurs.
    long readLong() throws IOException;
}
