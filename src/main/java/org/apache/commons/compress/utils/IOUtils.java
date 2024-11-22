/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.util.Objects;

/**
 * Utility functions.
 *
 * @Immutable (has mutable data but it is write-only).
 */
public final class IOUtils {

    /**
     * The default buffer size ({@value}) to use in copy methods.
     *
     * @since 1.27.1-0
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Empty array of type {@link OpenOption}.
     *
     * @since 1.27.1-0
     */
    public static final OpenOption[] EMPTY_OPEN_OPTIONS = {};

    /**
     * Empty array of type {@link LinkOption}.
     *
     * @since 1.21
     */
    public static final LinkOption[] EMPTY_LINK_OPTIONS = {};

    private static final byte[] SKIP_BUFFER = new byte[DEFAULT_BUFFER_SIZE];

    /**
     * Closes the given {@link Closeable} as a null-safe operation.
     *
     * @param closeable The resource to close, may be null.
     * @throws IOException if an I/O error occurs.
     * @since 1.27.1-0
     */
    public static void close(final Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Closes the given Closeable and swallows any IOException that may occur.
     *
     * @param closeable Closeable to close, can be null
     * @since 1.7
     */
    public static void closeQuietly(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * Copies the content of a InputStream into an OutputStream. Uses a default buffer size of 8192 bytes.
     *
     * @param input  the {@link InputStream} to read.
     * @param output the {@link OutputStream} to write.
     * @return the number of bytes copied.
     * @throws IOException if an error occurs.
     * @throws NullPointerException if the {@code input} or {@code output} is {@code null}.
     */
    public static long copy(final InputStream input, final OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copies the content of a InputStream into an OutputStream
     *
     * @param input the {@link InputStream} to read.
     * @param output the {@link OutputStream} to write.
     * @param bufferSize the buffer size to use, must be bigger than 0.
     * @return the number of bytes copied.
     * @throws IOException              if an error occurs.
     * @throws NullPointerException if the {@code input} or {@code output} is {@code null}.
     */
    public static long copy(final InputStream input, final OutputStream output, final int bufferSize) throws IOException {
        Objects.requireNonNull(input);
        Objects.requireNonNull(output);

        byte[] buffer = new byte[bufferSize];

        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Copies part of the content of a InputStream into an OutputStream. Uses a default buffer size of 8024 bytes.
     *
     * @param input  the InputStream to copy
     * @param output the target Stream
     * @param len    maximum amount of bytes to copy
     * @return the number of bytes copied
     * @throws IOException if an error occurs
     * @since 1.21
     */
    public static long copyRange(final InputStream input, final long len, final OutputStream output) throws IOException {
        return copyRange(input, len, output, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copies part of the content of a InputStream into an OutputStream
     *
     * @param input      the InputStream to copy
     * @param len        maximum amount of bytes to copy
     * @param output     the target, may be null to simulate output to dev/null on Linux and NUL on Windows
     * @param bufferSize the buffer size to use, must be bigger than 0
     * @return the number of bytes copied
     * @throws IOException              if an error occurs
     * @throws IllegalArgumentException if bufferSize is smaller than or equal to 0
     * @since 1.21
     */
    public static long copyRange(final InputStream input, final long len, final OutputStream output, final int bufferSize) throws IOException {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("bufferSize must be bigger than 0");
        }
        final byte[] buffer = new byte[(int) Math.min(bufferSize, Math.max(0, len))];
        int n = 0;
        long count = 0;
        while (count < len && -1 != (n = input.read(buffer, 0, (int) Math.min(len - count, buffer.length)))) {
            if (output != null) {
                output.write(buffer, 0, n);
            }
            count += n;
        }
        return count;
    }

    /**
     * Reads as much from input as possible to fill the given array.
     * <p>
     * This method may invoke read repeatedly to fill the array and only read less bytes than the length of the array if the end of the stream has been reached.
     * </p>
     *
     * @param input stream to read from
     * @param array buffer to fill
     * @return the number of bytes actually read
     * @throws IOException on error
     */
    public static int readFully(final InputStream input, final byte[] array) throws IOException {
        return readFully(input, array, 0, array.length);
    }

    /**
     * Reads as much from input as possible to fill the given array with the given amount of bytes.
     * <p>
     * This method may invoke read repeatedly to read the bytes and only read less bytes than the requested length if the end of the stream has been reached.
     * </p>
     *
     * @param input  stream to read from
     * @param array  buffer to fill
     * @param offset offset into the buffer to start filling at
     * @param length    of bytes to read
     * @return the number of bytes actually read
     * @throws IOException if an I/O error has occurred
     */
    public static int readFully(final InputStream input, final byte[] array, final int offset, final int length) throws IOException {
        if (length < 0 || offset < 0 || length + offset > array.length || length + offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        int x;
        int count = 0;
        while (count != length) {
            x = input.read(array, offset + count, length - count);
            if (x == -1) {
                break;
            }
            count += x;
        }
        return count;
    }

    /**
     * Reads {@code b.remaining()} bytes from the given channel starting at the current channel's position.
     * <p>
     * This method reads repeatedly from the channel until the requested number of bytes are read. This method blocks until the requested number of bytes are
     * read, the end of the channel is detected, or an exception is thrown.
     * </p>
     *
     * @param channel    the channel to read from
     * @param byteBuffer the buffer into which the data is read.
     * @throws IOException  if an I/O error occurs.
     * @throws EOFException if the channel reaches the end before reading all the bytes.
     */
    public static void readFully(final ReadableByteChannel channel, final ByteBuffer byteBuffer) throws IOException {
        final int expectedLength = byteBuffer.remaining();
        int read = 0;
        while (read < expectedLength) {
            final int readNow = channel.read(byteBuffer);
            if (readNow <= 0) {
                break;
            }
            read += readNow;
        }
        if (read < expectedLength) {
            throw new EOFException();
        }
    }

    /**
     * Gets part of the contents of an {@code InputStream} as a {@code byte[]}.
     *
     * @param input the {@code InputStream} to read from
     * @param length   maximum amount of bytes to copy
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     * @since 1.21
     */
    public static byte[] readRange(final InputStream input, final int length) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyRange(input, length, output);
        return output.toByteArray();
    }

    /**
     * Gets part of the contents of an {@code ReadableByteChannel} as a {@code byte[]}.
     *
     * @param input the {@code ReadableByteChannel} to read from
     * @param length   maximum amount of bytes to copy
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     * @since 1.21
     */
    public static byte[] readRange(final ReadableByteChannel input, final int length) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteBuffer b = ByteBuffer.allocate(Math.min(length, DEFAULT_BUFFER_SIZE));
        int read = 0;
        while (read < length) {
            // Make sure we never read more than len bytes
            b.limit(Math.min(length - read, b.capacity()));
            final int readCount = input.read(b);
            if (readCount <= 0) {
                break;
            }
            output.write(b.array(), 0, readCount);
            b.rewind();
            read += readCount;
        }
        return output.toByteArray();
    }

    /**
     * Skips bytes from an input byte stream.
     * <p>
     * This method will only skip less than the requested number of bytes if the end of the input stream has been reached.
     * </p>
     *
     * @param input     stream to skip bytes in
     * @param toSkip the number of bytes to skip
     * @return the number of bytes actually skipped
     * @throws IOException on error
     */
    public static long skip(final InputStream input, final long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        }
        //
        // No need to synchronize access to SCRATCH_BYTE_BUFFER_WO: We don't care if the buffer is written multiple
        // times or in parallel since the data is ignored. We reuse the same buffer, if the buffer size were variable or read-write,
        // we would need to synch or use a thread local to ensure some other thread safety.
        //
        long remain = toSkip;
        while (remain > 0) {
            // See https://issues.apache.org/jira/browse/IO-203 for why we use read() rather than delegating to skip()
            final long n = input.read(SKIP_BUFFER, 0, (int) Math.min(remain, SKIP_BUFFER.length));
            if (n < 0) { // EOF
                break;
            }
            remain -= n;
        }
        return toSkip - remain;
    }

    /**
     * Gets the contents of an {@code InputStream} as a {@code byte[]}.
     * <p>
     * This method buffers the input internally, so there is no need to use a {@code BufferedInputStream}.
     * </p>
     *
     * @param input the {@code InputStream} to read from
     * @return the requested byte array
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     * @since 1.5
     */
    public static byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    /** Private constructor to prevent instantiation of this utility class. */
    private IOUtils() {
    }

}
