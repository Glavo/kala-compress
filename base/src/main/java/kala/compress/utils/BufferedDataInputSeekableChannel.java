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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/// This class provides a buffered implementation of the `DataInputSeekableChannel` interface.
///
/// It uses a read buffer to cache data read from the underlying channel, reducing the number of I/O operations.
///
/// @author Glavo
/// @NotThreadSafe
/// @since 1.27.1-2
public final class BufferedDataInputSeekableChannel implements DataInputSeekableChannel {
    private final SeekableByteChannel channel;
    private final ByteBuffer readBuffer;

    /// Creates a new instance with the given buffer size and byte order.
    public BufferedDataInputSeekableChannel(SeekableByteChannel channel, int bufferSize, ByteOrder byteOrder) {
        this(channel, ByteBuffer.allocate(bufferSize).order(byteOrder));
    }

    /// Creates a new instance with the given read buffer.
    ///
    /// The buffer size must be at least 8 bytes.
    ///
    /// @param channel    the underlying channel.
    /// @param readBuffer the read buffer.
    public BufferedDataInputSeekableChannel(SeekableByteChannel channel, ByteBuffer readBuffer) {
        this.channel = channel;
        this.readBuffer = readBuffer;

        if (readBuffer.capacity() < 8) {
            throw new IllegalArgumentException("Read buffer capacity must be at least 8 bytes");
        }

        if (readBuffer.isReadOnly()) {
            throw new IllegalArgumentException("Read buffer must not be read-only");
        }

        readBuffer.position(0).limit(0);
    }

    /// Ensure that this channel is open.
    ///
    /// @throws ClosedChannelException if this channel is closed.
    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    /// Ensure that the read buffer has at least `required` bytes remaining.
    ///
    /// @throws java.io.EOFException if the end of the stream is reached before `required` bytes are available.
    /// @throws IOException          if an I/O error occurs.
    private void ensureBufferRemaining(int required) throws IOException {
        if (readBuffer.remaining() < required) {
            readBuffer.compact();
            try {
                while (readBuffer.position() < required) {
                    int n = channel.read(readBuffer);
                    if (n <= 0) {
                        throw new EOFException();
                    }
                }
            } finally {
                readBuffer.flip();
            }
        }
    }

    /// Make sure the read buffer is empty.
    private void clearReadBuffer() {
        readBuffer.position(0).limit(0);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        clearReadBuffer();
        channel.close();
    }

    /// Always throws {@link NonWritableChannelException} since this implementation is read-only
    ///
    /// @throws NonWritableChannelException always.
    @Override
    public int write(ByteBuffer src) throws NonWritableChannelException {
        throw new NonWritableChannelException();
    }

    /// Always throws {@link NonWritableChannelException} since this implementation is read-only
    ///
    /// @throws NonWritableChannelException always.
    @Override
    public SeekableByteChannel truncate(long size) throws NonWritableChannelException {
        throw new NonWritableChannelException();
    }

    @Override
    public ByteOrder byteOrder() {
        return readBuffer.order();
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public long position() throws IOException {
        long rawPosition = channel.position();
        long currentPosition = rawPosition - readBuffer.remaining();
        assert currentPosition >= 0 : "Invalid position: " + currentPosition + ", raw position: " + rawPosition + ", remaining cache: " + readBuffer.remaining();
        return currentPosition;
    }

    @Override
    public BufferedDataInputSeekableChannel position(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IllegalArgumentException("Position must be non-negative: " + newPosition);
        }

        long rawPosition = channel.position();
        long currentPosition = rawPosition - readBuffer.remaining();
        assert currentPosition >= 0;

        if (newPosition == currentPosition) {
            return this;
        } else if (newPosition > currentPosition) {
            long nSkip = newPosition - currentPosition;
            int remaining = readBuffer.remaining();

            if (nSkip >= remaining) {
                clearReadBuffer();
                channel.position(newPosition);
            } else {
                readBuffer.position((int) (readBuffer.position() + nSkip));
            }
        } else { // newPosition < currentPosition
            clearReadBuffer();
            channel.position(newPosition);
        }
        return this;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int remaining = readBuffer.remaining();
        if (remaining == 0) {
            if (dst.remaining() >= readBuffer.capacity()) {
                // When the target buffer is large enough, we don't need to use the read buffer.
                return channel.read(dst);
            }

            readBuffer.compact();
            int n = channel.read(readBuffer);
            readBuffer.flip();
            remaining = readBuffer.remaining();

            assert n == remaining || (n == -1 && remaining == 0);

            if (n <= 0) {
                return n;
            }
        } else {
            ensureOpen();
        }

        int nRead = Math.min(dst.remaining(), remaining);
        if (nRead == remaining) {
            dst.put(readBuffer);
            clearReadBuffer();
        } else {
            int oldLimit = readBuffer.limit();
            readBuffer.limit(readBuffer.position() + nRead);
            dst.put(readBuffer);
            readBuffer.limit(oldLimit);
        }
        return nRead;
    }

    @Override
    public byte readByte() throws IOException {
        ensureBufferRemaining(Byte.BYTES);
        return readBuffer.get();
    }

    @Override
    public short readShort() throws IOException {
        ensureBufferRemaining(Short.BYTES);
        return readBuffer.getShort();
    }

    @Override
    public int readInt() throws IOException {
        ensureBufferRemaining(Integer.BYTES);
        return readBuffer.getInt();
    }

    @Override
    public long readLong() throws IOException {
        ensureBufferRemaining(Long.BYTES);
        return readBuffer.getLong();
    }
}
