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
package kala.compress.compressors;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import kala.compress.AbstractTestCase;
import kala.compress.compressors.lzma.LZMACompressorInputStream;
import kala.compress.utils.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public final class LZMATestCase extends AbstractTestCase {

    @Test
    public void lzmaRoundtrip() throws Exception {
        final File input = getFile("test1.xml");
        final File compressed = new File(dir, "test1.xml.xz");
        try (OutputStream out = Files.newOutputStream(compressed.toPath())) {
            try (CompressorOutputStream cos = new CompressorStreamFactory()
                    .createCompressorOutputStream("lzma", out)) {
                IOUtils.copy(Files.newInputStream(input.toPath()), cos);
            }
        }
        byte[] orig;
        try (InputStream is = Files.newInputStream(input.toPath())) {
            orig = IOUtils.toByteArray(is);
        }
        final byte[] uncompressed;
        try (InputStream is = Files.newInputStream(compressed.toPath());
             CompressorInputStream in = new LZMACompressorInputStream(is)) {
            uncompressed = IOUtils.toByteArray(in);
        }
        Assert.assertArrayEquals(orig, uncompressed);
    }

    @Test
    public void testLZMAUnarchive() throws Exception {
        final File input = getFile("bla.tar.lzma");
        final File output = new File(dir, "bla.tar");
        try (InputStream is = Files.newInputStream(input.toPath())) {
            final CompressorInputStream in = new LZMACompressorInputStream(is);
            copy(in, output);
        }
    }

    @Test
    public void testLZMAUnarchiveWithAutodetection() throws Exception {
        final File input = getFile("bla.tar.lzma");
        final File output = new File(dir, "bla.tar");
        try (InputStream is = new BufferedInputStream(Files.newInputStream(input.toPath()))) {
            final CompressorInputStream in = new CompressorStreamFactory()
                    .createCompressorInputStream(is);
            copy(in, output);
        }
    }

    @Test
    public void singleByteReadConsistentlyReturnsMinusOneAtEof() throws IOException {
        final File input = getFile("bla.tar.lzma");
        try (InputStream is = Files.newInputStream(input.toPath())) {
            final LZMACompressorInputStream in =
                    new LZMACompressorInputStream(is);
            IOUtils.toByteArray(in);
            Assert.assertEquals(-1, in.read());
            Assert.assertEquals(-1, in.read());
            in.close();
        }
    }

    @Test
    public void multiByteReadConsistentlyReturnsMinusOneAtEof() throws IOException {
        final File input = getFile("bla.tar.lzma");
        final byte[] buf = new byte[2];
        try (InputStream is = Files.newInputStream(input.toPath())) {
            final LZMACompressorInputStream in =
                    new LZMACompressorInputStream(is);
            IOUtils.toByteArray(in);
            Assert.assertEquals(-1, in.read(buf));
            Assert.assertEquals(-1, in.read(buf));
            in.close();
        }
    }

    private void copy(final InputStream in, final File output) throws IOException {
        try (OutputStream out = Files.newOutputStream(output.toPath())) {
            IOUtils.copy(in, out);
        } finally {
            in.close();
        }
    }
}
