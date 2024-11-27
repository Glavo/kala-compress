/*
 * Copyright 2024 Glavo
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
package org.apache.commons.compress.archivers.sevenz;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;

/**
 * @author Glavo
 * @since 1.21.0.1
 */
final class SevenZArchiver extends ArchiveStreamFactory.BuiltinArchiver {
    public SevenZArchiver() {
        super(ArchiveStreamFactory.SEVEN_Z);
    }

    @Override
    public boolean matches(byte[] signature, int length) {
        return SevenZArchiveReader.matches(signature, length);
    }
}