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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * An entry of an archive.
 */
public interface ArchiveEntry {

    /**
     * Special value ({@value}) indicating that the size is unknown.
     */
    long SIZE_UNKNOWN = -1;

    /**
     * Gets the last modified time of this entry.
     *
     * @return the last modified time of this entry.
     * @since 1.27.1-0
     */
    FileTime getLastModifiedTime();

    /**
     * Gets the name of the entry in this archive. May refer to a file or directory or other item.
     * <p>
     * This method returns the raw name as it is stored inside of the archive.
     * </p>
     *
     * @return The name of this entry in the archive.
     */
    String getName();

    /**
     * Gets the uncompressed size of this entry. May be -1 (SIZE_UNKNOWN) if the size is unknown
     *
     * @return the uncompressed size of this entry.
     */
    long getSize();

    /**
     * Tests whether this entry refers to a directory (true).
     *
     * @return true if this entry refers to a directory.
     */
    boolean isDirectory();

    /**
     * Resolves this entry in the given parent Path.
     *
     * @param parentPath the {@link Path#resolve(Path)} receiver.
     * @return a resolved and normalized Path.
     * @throws IOException if this method detects a Zip slip.
     * @since 1.26.0
     */
    default Path resolveIn(final Path parentPath) throws IOException {
        final String name = getName();
        final Path outputFile = parentPath.resolve(name).normalize();
        if (!outputFile.startsWith(parentPath)) {
            throw new IOException(String.format("Zip slip '%s' + '%s' -> '%s'", parentPath, name, outputFile));
        }
        return outputFile;
    }

}