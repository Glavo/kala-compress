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

module kala.compress.archivers.zip {
    requires transitive kala.compress.base;
    requires static kala.compress.compressors.bzip2;
    requires static kala.compress.compressors.deflate64;

    exports kala.compress.archivers.jar;
    exports kala.compress.archivers.zip;

    opens kala.compress.archivers.jar to kala.compress.base;
    opens kala.compress.archivers.zip to kala.compress.base;
}