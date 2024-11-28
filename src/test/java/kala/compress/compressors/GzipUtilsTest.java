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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kala.compress.compressors.gzip.GzipUtils;
import org.junit.jupiter.api.Test;

public class GzipUtilsTest {

    @Test
    public void testGetCompressedFilename() {
        assertEquals(".gz", GzipUtils.getCompressedFileName(""));
        assertEquals(".gz", GzipUtils.getCompressedFileName(""));
        assertEquals("x.gz", GzipUtils.getCompressedFileName("x"));
        assertEquals("x.gz", GzipUtils.getCompressedFileName("x"));

        assertEquals("x.tgz", GzipUtils.getCompressedFileName("x.tar"));
        assertEquals("x.tgz", GzipUtils.getCompressedFileName("x.tar"));
        assertEquals("x.svgz", GzipUtils.getCompressedFileName("x.svg"));
        assertEquals("x.svgz", GzipUtils.getCompressedFileName("x.svg"));
        assertEquals("x.cpgz", GzipUtils.getCompressedFileName("x.cpio"));
        assertEquals("x.cpgz", GzipUtils.getCompressedFileName("x.cpio"));
        assertEquals("x.wmz", GzipUtils.getCompressedFileName("x.wmf"));
        assertEquals("x.wmz", GzipUtils.getCompressedFileName("x.wmf"));
        assertEquals("x.emz", GzipUtils.getCompressedFileName("x.emf"));
        assertEquals("x.emz", GzipUtils.getCompressedFileName("x.emf"));

        assertEquals("x.svgz", GzipUtils.getCompressedFileName("x.SVG"));
        assertEquals("x.svgz", GzipUtils.getCompressedFileName("x.SVG"));
        assertEquals("X.svgz", GzipUtils.getCompressedFileName("X.SVG"));
        assertEquals("X.svgz", GzipUtils.getCompressedFileName("X.SVG"));
        assertEquals("X.svgz", GzipUtils.getCompressedFileName("X.svG"));
        assertEquals("X.svgz", GzipUtils.getCompressedFileName("X.svG"));

        assertEquals("x.wmf .gz", GzipUtils.getCompressedFileName("x.wmf "));
        assertEquals("x.wmf .gz", GzipUtils.getCompressedFileName("x.wmf "));
        assertEquals("x.wmf\n.gz", GzipUtils.getCompressedFileName("x.wmf\n"));
        assertEquals("x.wmf\n.gz", GzipUtils.getCompressedFileName("x.wmf\n"));
        assertEquals("x.wmf.y.gz", GzipUtils.getCompressedFileName("x.wmf.y"));
        assertEquals("x.wmf.y.gz", GzipUtils.getCompressedFileName("x.wmf.y"));
    }

    @Test
    public void testGetUncompressedFilename() {
        assertEquals("", GzipUtils.getUncompressedFileName(""));
        assertEquals("", GzipUtils.getUncompressedFileName(""));
        assertEquals(".gz", GzipUtils.getUncompressedFileName(".gz"));
        assertEquals(".gz", GzipUtils.getUncompressedFileName(".gz"));

        assertEquals("x.tar", GzipUtils.getUncompressedFileName("x.tgz"));
        assertEquals("x.tar", GzipUtils.getUncompressedFileName("x.tgz"));
        assertEquals("x.tar", GzipUtils.getUncompressedFileName("x.taz"));
        assertEquals("x.tar", GzipUtils.getUncompressedFileName("x.taz"));
        assertEquals("x.svg", GzipUtils.getUncompressedFileName("x.svgz"));
        assertEquals("x.svg", GzipUtils.getUncompressedFileName("x.svgz"));
        assertEquals("x.cpio", GzipUtils.getUncompressedFileName("x.cpgz"));
        assertEquals("x.cpio", GzipUtils.getUncompressedFileName("x.cpgz"));
        assertEquals("x.wmf", GzipUtils.getUncompressedFileName("x.wmz"));
        assertEquals("x.wmf", GzipUtils.getUncompressedFileName("x.wmz"));
        assertEquals("x.emf", GzipUtils.getUncompressedFileName("x.emz"));
        assertEquals("x.emf", GzipUtils.getUncompressedFileName("x.emz"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x.gz"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x.gz"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x.z"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x.z"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x-gz"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x-gz"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x-z"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x-z"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x_z"));
        assertEquals("x", GzipUtils.getUncompressedFileName("x_z"));

        assertEquals("x.svg", GzipUtils.getUncompressedFileName("x.SVGZ"));
        assertEquals("x.svg", GzipUtils.getUncompressedFileName("x.SVGZ"));
        assertEquals("X.svg", GzipUtils.getUncompressedFileName("X.SVGZ"));
        assertEquals("X.svg", GzipUtils.getUncompressedFileName("X.SVGZ"));
        assertEquals("X.svg", GzipUtils.getUncompressedFileName("X.svGZ"));
        assertEquals("X.svg", GzipUtils.getUncompressedFileName("X.svGZ"));

        assertEquals("x.wmz ", GzipUtils.getUncompressedFileName("x.wmz "));
        assertEquals("x.wmz ", GzipUtils.getUncompressedFileName("x.wmz "));
        assertEquals("x.wmz\n", GzipUtils.getUncompressedFileName("x.wmz\n"));
        assertEquals("x.wmz\n", GzipUtils.getUncompressedFileName("x.wmz\n"));
        assertEquals("x.wmz.y", GzipUtils.getUncompressedFileName("x.wmz.y"));
        assertEquals("x.wmz.y", GzipUtils.getUncompressedFileName("x.wmz.y"));
    }

    @Test
    public void testIsCompressedFilename() {
        assertFalse(GzipUtils.isCompressedFileName(""));
        assertFalse(GzipUtils.isCompressedFileName(""));
        assertFalse(GzipUtils.isCompressedFileName(".gz"));
        assertFalse(GzipUtils.isCompressedFileName(".gz"));

        assertTrue(GzipUtils.isCompressedFileName("x.tgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.tgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.taz"));
        assertTrue(GzipUtils.isCompressedFileName("x.taz"));
        assertTrue(GzipUtils.isCompressedFileName("x.svgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.svgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.cpgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.cpgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.wmz"));
        assertTrue(GzipUtils.isCompressedFileName("x.wmz"));
        assertTrue(GzipUtils.isCompressedFileName("x.emz"));
        assertTrue(GzipUtils.isCompressedFileName("x.emz"));
        assertTrue(GzipUtils.isCompressedFileName("x.gz"));
        assertTrue(GzipUtils.isCompressedFileName("x.gz"));
        assertTrue(GzipUtils.isCompressedFileName("x.z"));
        assertTrue(GzipUtils.isCompressedFileName("x.z"));
        assertTrue(GzipUtils.isCompressedFileName("x-gz"));
        assertTrue(GzipUtils.isCompressedFileName("x-gz"));
        assertTrue(GzipUtils.isCompressedFileName("x-z"));
        assertTrue(GzipUtils.isCompressedFileName("x-z"));
        assertTrue(GzipUtils.isCompressedFileName("x_z"));
        assertTrue(GzipUtils.isCompressedFileName("x_z"));

        assertFalse(GzipUtils.isCompressedFileName("xxgz"));
        assertFalse(GzipUtils.isCompressedFileName("xxgz"));
        assertFalse(GzipUtils.isCompressedFileName("xzz"));
        assertFalse(GzipUtils.isCompressedFileName("xzz"));
        assertFalse(GzipUtils.isCompressedFileName("xaz"));
        assertFalse(GzipUtils.isCompressedFileName("xaz"));

        assertTrue(GzipUtils.isCompressedFileName("x.SVGZ"));
        assertTrue(GzipUtils.isCompressedFileName("x.SVGZ"));
        assertTrue(GzipUtils.isCompressedFileName("x.Svgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.Svgz"));
        assertTrue(GzipUtils.isCompressedFileName("x.svGZ"));
        assertTrue(GzipUtils.isCompressedFileName("x.svGZ"));

        assertFalse(GzipUtils.isCompressedFileName("x.wmz "));
        assertFalse(GzipUtils.isCompressedFileName("x.wmz "));
        assertFalse(GzipUtils.isCompressedFileName("x.wmz\n"));
        assertFalse(GzipUtils.isCompressedFileName("x.wmz\n"));
        assertFalse(GzipUtils.isCompressedFileName("x.wmz.y"));
        assertFalse(GzipUtils.isCompressedFileName("x.wmz.y"));
    }

}
