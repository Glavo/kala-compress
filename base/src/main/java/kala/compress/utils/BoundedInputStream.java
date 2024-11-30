/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package kala.compress.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A stream that limits reading from a wrapped stream to a given number of bytes.
 *
 * @NotThreadSafe
 * @since 1.6
 */
public class BoundedInputStream extends FilterInputStream {
    /**
     * The current count of bytes counted.
     */
    private long count;

    /**
     * The max count of bytes to read.
     */
    private final long maxCount;

    /**
     * Flag if close should be propagated.
     */
    private final boolean propagateClose;

    /**
     * The current mark.
     */
    private long mark;

    /**
     * Creates the stream that will at most read the given amount of bytes from the given stream.
     *
     * @param in       the stream to read from
     * @param maxCount the maximum amount of bytes to read
     */
    public BoundedInputStream(final InputStream in, final long maxCount) {
        super(in);
        this.maxCount = maxCount;
        this.propagateClose = false;
    }

    /**
     * Creates the stream that will at most read the given amount of bytes from the given stream.
     *
     * @param in             the stream to read from
     * @param maxCount       the maximum amount of bytes to read
     * @param propagateClose {@code true} if calling {@link #close()} propagates to the {@code close()} method of
     *                       the underlying stream or {@code false} if it does not.
     * @since 1.27.1-0
     */
    public BoundedInputStream(final InputStream in, final long maxCount, final boolean propagateClose) {
        super(in);
        this.maxCount = maxCount;
        this.propagateClose = propagateClose;
    }

    @Override
    public void close() throws IOException {
        if (propagateClose) {
            in.close();
        }
    }

    /**
     * Returns the current number of bytes read from this stream.
     *
     * @return the number of read bytes
     * @since 1.27.1-0
     */
    public long getBytesRead() {
        return count;
    }

    /**
     * Gets the max count of bytes to read.
     *
     * @return The max count of bytes to read.
     * @since 1.27.1-0
     */
    public long getMaxCount() {
        return maxCount;
    }

    /**
     * Gets how many bytes remain to read.
     *
     * @return bytes how many bytes remain to read.
     * @since 1.21
     */
    public long getBytesRemaining() {
        return maxCount - count;
    }

    @Override
    public int read() throws IOException {
        if (count < maxCount) {
            count++;
            return in.read();
        }
        return -1;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        long bytesRemaining = getBytesRemaining();
        if (bytesRemaining == 0) {
            return -1;
        }
        int bytesToRead = (int) Math.min(len, bytesRemaining);
        final int bytesRead = in.read(b, off, bytesToRead);
        if (bytesRead >= 0) {
            count += bytesRead;
        }
        return bytesRead;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.20
     */
    @Override
    public long skip(final long n) throws IOException {
        final long bytesToSkip = Math.min(getBytesRemaining(), n);
        final long bytesSkipped = in.skip(bytesToSkip);
        count += bytesSkipped;

        return bytesSkipped;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.27.1-0
     */
    @Override
    public void mark(final int readLimit) {
        in.mark(readLimit);
        mark = count;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.27.1-0
     */
    @Override
    public void reset() throws IOException {
        in.reset();
        count = mark;
    }
}
