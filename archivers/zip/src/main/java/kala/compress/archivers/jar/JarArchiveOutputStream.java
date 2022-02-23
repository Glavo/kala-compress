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
package kala.compress.archivers.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.zip.JarMarker;
import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveOutputStream;
import kala.compress.utils.Charsets;

/**
 * Subclass that adds a special extra field to the very first entry
 * which allows the created archive to be used as an executable jar on
 * Solaris.
 *
 * @NotThreadSafe
 */
public class JarArchiveOutputStream extends ZipArchiveOutputStream {

    private boolean jarMarkerAdded;

    public JarArchiveOutputStream(final OutputStream out) {
        super(out);
    }

    /**
     * Create and instance that wraps the output stream using the provided encoding.
     *
     * @param out the output stream to wrap
     * @param charset the charset to use. Use null for the UTF-8.
     * @since 1.21.0.1
     */
    public JarArchiveOutputStream(final OutputStream out, final Charset charset) {
        super(out, charset);
    }

    // @throws ClassCastException if entry is not an instance of ZipArchiveEntry
    @Override
    public void putArchiveEntry(final ArchiveEntry ze) throws IOException {
        if (!jarMarkerAdded) {
            ((ZipArchiveEntry)ze).addAsFirstExtraField(JarMarker.getInstance());
            jarMarkerAdded = true;
        }
        super.putArchiveEntry(ze);
    }
}
