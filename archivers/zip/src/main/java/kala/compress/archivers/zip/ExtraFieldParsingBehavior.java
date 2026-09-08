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

import java.util.zip.ZipException;

/**
 * Controls details of parsing ZIP extra fields.
 *
 * @since 1.19
 */
public interface ExtraFieldParsingBehavior extends UnparseableExtraFieldBehavior {

    /// Creates an extra field for the given identifier.
    /// A default implementation may delegate to [ExtraFieldUtils#createExtraField(short)].
    ///
    /// @param headerId the unsigned 16-bit identifier as a raw bit pattern
    /// @return an extra field, never null
    /// @throws ZipException if creation fails
    /// @throws InstantiationException if the class cannot be instantiated
    /// @throws IllegalAccessException if instantiating the class is not permitted
    ZipExtraField createExtraField(short headerId) throws ZipException, InstantiationException, IllegalAccessException;

    /**
     * Fills in the extra field data for a single extra field.
     * <p>
     * A good default implementation would be {@link ExtraFieldUtils#fillExtraField}.
     * </p>
     *
     * @param field the extra field instance to fill
     * @param data  the array of extra field data
     * @param off   offset into data where this field's data starts
     * @param len   the length of this field's data
     * @param local whether the extra field data stems from the local file header. If this is false then the data is part if the central directory header extra
     *              data.
     * @return the filled field. Usually this is the same as {@code
     * field} but it could be a replacement extra field as well. Must not be {@code null}.
     * @throws ZipException if an error occurs
     */
    ZipExtraField fill(ZipExtraField field, byte[] data, int off, int len, boolean local) throws ZipException;
}
