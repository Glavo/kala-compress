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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestCompressorStreamProvider implements CompressorStreamProvider {

    public static final class InvocationConfirmationException extends CompressorException {

        private static final long serialVersionUID = 1L;

        public InvocationConfirmationException(final String message) {
            super(message);
        }
    }

    @Override
    public CompressorInputStream createCompressorInputStream(final String name, final InputStream in,
            final boolean decompressConcatenated) throws CompressorException {
        throw new InvocationConfirmationException(name);
    }

    @Override
    public CompressorOutputStream createCompressorOutputStream(final String name, final OutputStream out)
            throws CompressorException {
        throw new InvocationConfirmationException(name);
    }

    @Override
    public Set<String> getInputStreamCompressorNames() {
        final String[] elements = new String[]{"TestInput1"};
        final HashSet<String> set = new HashSet<>(elements.length);
        Collections.addAll(set, elements);
        return set;
    }

    @Override
    public Set<String> getOutputStreamCompressorNames() {
        final String[] elements = new String[]{"TestOutput1"};
        final HashSet<String> set = new HashSet<>(elements.length);
        Collections.addAll(set, elements);
        return set;
    }

}
