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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import kala.compress.utils.InputStreamStatistics;

/// Reads raw DEFLATE data with trailing padding and byte counts.
///
/// Closing this stream closes the input and releases the supplied inflater.
///
/// @since 1.17
class InflaterInputStreamWithStatistics extends InflaterInputStream implements InputStreamStatistics {

    /// Number of compressed bytes read from the input, excluding padding.
    private long compressedCount;
    /// Number of decompressed bytes read.
    private long uncompressedCount;
    /// Whether the trailing padding byte has been supplied.
    private boolean eof;

    /// Creates a stream using the supplied raw DEFLATE inflater and input buffer size.
    InflaterInputStreamWithStatistics(final InputStream in, final Inflater inf, final int size) {
        super(in, inf, size);
    }

    /// Closes the input and releases the inflater, even if closing the input fails.
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            inf.end();
        }
    }

    /// Fills the input buffer, supplying one trailing zero byte as required by [Inflater#Inflater(boolean)].
    @Override
    protected void fill() throws IOException {
        if (eof) {
            throw new EOFException("Unexpected end of ZLIB input stream");
        }
        len = in.read(buf, 0, buf.length);
        if (len == -1) {
            buf[0] = 0;
            len = 1;
            eof = true;
        } else {
            compressedCount += len;
        }
        inf.setInput(buf, 0, len);
    }

    /// Returns the number of compressed bytes read from the input, excluding padding.
    @Override
    public long getCompressedCount() {
        return compressedCount;
    }

    @Override
    public long getUncompressedCount() {
        return uncompressedCount;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int bytes = super.read(b, off, len);
        if (bytes > -1) {
            uncompressedCount += bytes;
        }
        return bytes;
    }
}
