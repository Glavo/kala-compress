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
package kala.compress.utils;

import org.junit.Test;

import java.util.NoSuchElementException;

import static org.junit.Assert.assertFalse;

/**
 * Unit tests for class {@link ServiceLoaderIterator kala.compress.utils.ServiceLoaderIterator}.
 *
 * @date 13.06.2017
 * @see ServiceLoaderIterator
 **/
public class ServiceLoaderIteratorTest {



    @Test(expected = NoSuchElementException.class)
    public void testNextThrowsNoSuchElementException() {

        final Class<String> clasz = String.class;
        final ServiceLoaderIterator<String> serviceLoaderIterator = new ServiceLoaderIterator<>(clasz);

        serviceLoaderIterator.next();

    }


    @Test
    public void testHasNextReturnsFalse() {

        final Class<Object> clasz = Object.class;
        final ServiceLoaderIterator<Object> serviceLoaderIterator = new ServiceLoaderIterator<>(clasz);
        final boolean result = serviceLoaderIterator.hasNext();

        assertFalse(result);

    }


    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveThrowsUnsupportedOperationException() {

        final Class<Integer> clasz = Integer.class;
        final ServiceLoaderIterator<Integer> serviceLoaderIterator = new ServiceLoaderIterator<>(clasz);

        serviceLoaderIterator.remove();


    }



}