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

package kala.compress.archivers.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/// Tests raw DEFLATE buffering, padding, statistics, and resource cleanup.
class InflaterInputStreamWithStatisticsTest {

    /// Compresses data as raw DEFLATE.
    private static byte[] compress(final byte[] data) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater)) {
            stream.write(data);
        } finally {
            deflater.end();
        }
        return output.toByteArray();
    }

    /// Checks boundary sizes, short input reads, mixed output reads, and byte counts.
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8191, 8192, 8193, 65536})
    void testRoundTrip(final int size) throws IOException {
        final byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        final byte[] compressed = compress(data);
        for (final int inputChunk : new int[] {17, 8192}) {
            final InputStream input = new ByteArrayInputStream(compressed) {
                @Override
                public synchronized int read(final byte[] b, final int off, final int len) {
                    return super.read(b, off, Math.min(len, inputChunk));
                }
            };
            try (InflaterInputStreamWithStatistics stream = new InflaterInputStreamWithStatistics(input, new Inflater(true), 8192)) {
                final ByteArrayOutputStream output = new ByteArrayOutputStream();
                final byte[] buffer = new byte[37];
                int first;
                while ((first = stream.read()) != -1) {
                    output.write(first);
                    assertEquals(output.size(), stream.getUncompressedCount());
                    final int count = stream.read(buffer);
                    if (count != -1) {
                        output.write(buffer, 0, count);
                    }
                    assertEquals(output.size(), stream.getUncompressedCount());
                }
                assertArrayEquals(data, output.toByteArray());
                assertEquals(compressed.length, stream.getCompressedCount());
                assertEquals(size, stream.getUncompressedCount());
                assertEquals(-1, stream.read());
            }
        }
    }

    /// Supplies padding once without counting it as source data.
    @Test
    void testPadding() throws IOException {
        final Inflater inflater = new Inflater(true);
        try (InflaterInputStreamWithStatistics stream = new InflaterInputStreamWithStatistics(InputStream.nullInputStream(), inflater, 8192)) {
            stream.fill();
            assertEquals(1, inflater.getRemaining());
            assertEquals(0, stream.getCompressedCount());
            assertThrows(EOFException.class, stream::fill);
        }
    }

    /// Rejects truncated input after the single padding byte has been exhausted.
    @Test
    void testTruncatedInput() throws IOException {
        final byte[] data = new byte[32768];
        new Random(42).nextBytes(data);
        final byte[] compressed = compress(data);
        final InputStream input = new ByteArrayInputStream(Arrays.copyOf(compressed, compressed.length / 2));
        try (InflaterInputStreamWithStatistics stream = new InflaterInputStreamWithStatistics(input, new Inflater(true), 8192)) {
            assertThrows(EOFException.class, stream::readAllBytes);
            assertEquals(compressed.length / 2, stream.getCompressedCount());
        }
    }

    /// Propagates input failures without replacing them with padding.
    @Test
    void testReadFailure() throws IOException {
        final EOFException failure = new EOFException("Input failure");
        final InputStream input = new FilterInputStream(InputStream.nullInputStream()) {
            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                throw failure;
            }
        };
        try (InflaterInputStreamWithStatistics stream = new InflaterInputStreamWithStatistics(input, new Inflater(true), 8192)) {
            assertSame(failure, assertThrows(EOFException.class, stream::read));
            assertEquals(0, stream.getCompressedCount());
        }
    }

    /// Releases the inflater even when closing the input fails.
    @Test
    void testCloseFailure() {
        final AtomicBoolean ended = new AtomicBoolean();
        final Inflater inflater = new Inflater(true) {
            @Override
            public void end() {
                ended.set(true);
                super.end();
            }
        };
        final IOException failure = new IOException("Close failure");
        final InputStream input = new FilterInputStream(InputStream.nullInputStream()) {
            @Override
            public void close() throws IOException {
                throw failure;
            }
        };
        final InflaterInputStreamWithStatistics stream = new InflaterInputStreamWithStatistics(input, inflater, 8192);
        try {
            assertSame(failure, assertThrows(IOException.class, stream::close));
            assertTrue(ended.get());
        } finally {
            inflater.end();
        }
    }
}
