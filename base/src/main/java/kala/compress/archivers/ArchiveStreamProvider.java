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

package kala.compress.archivers;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * Creates Archive {@link ArchiveInputStream}s and {@link ArchiveOutputStream}s.
 *
 * @since 1.13
 */
public interface ArchiveStreamProvider {

    /**
     * Creates an archive input stream from an archiver name and an input stream.
     *
     * @param <I>          The {@link ArchiveInputStream} type.
     * @param archiverName the archiver name, i.e. {@value kala.compress.archivers.ArchiveStreamFactory#AR},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#ARJ},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#ZIP},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#TAR},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#JAR},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#CPIO},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#DUMP} or
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#SEVEN_Z}
     * @param inputStream  the input stream
     * @param encoding     encoding name or null for the default
     * @return the archive input stream
     * @throws ArchiveException               if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be read from a stream
     * @throws IllegalArgumentException       if the archiver name or stream is null
     */
    <I extends ArchiveInputStream<? extends ArchiveEntry>> I createArchiveInputStream(String archiverName, InputStream inputStream, Charset encoding)
            throws ArchiveException;

    /**
     * Creates an archive output stream from an archiver name and an output stream.
     *
     * @param <O>          The {@link ArchiveInputStream} type.
     * @param archiverName the archiver name, i.e. {@value kala.compress.archivers.ArchiveStreamFactory#AR},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#ZIP},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#TAR},
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#JAR} or
     *                     {@value kala.compress.archivers.ArchiveStreamFactory#CPIO}
     * @param outputStream the output stream
     * @param encoding     encoding name or null for the default
     * @return the archive output stream
     * @throws ArchiveException               if the archiver name is not known
     * @throws StreamingNotSupportedException if the format cannot be written to a stream
     * @throws IllegalArgumentException       if the archiver name or stream is null
     */
    <O extends ArchiveOutputStream<? extends ArchiveEntry>> O createArchiveOutputStream(String archiverName, OutputStream outputStream, Charset encoding)
            throws ArchiveException;

    /**
     * Gets all the input stream archive names for this provider
     *
     * @return all the input archive names for this provider
     */
    Set<String> getInputStreamArchiveNames();

    /**
     * Gets all the output stream archive names for this provider
     *
     * @return all the output archive names for this provider
     */
    Set<String> getOutputStreamArchiveNames();

}
