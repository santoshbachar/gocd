/*
 * Copyright Thoughtworks, Inc.
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
package com.thoughtworks.go.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSizeUtilsTest {

    @Test
    void shouldConvertBytes() {
        assertThat(FileSizeUtils.byteCountToDisplaySize(1023)).isEqualTo("1023 bytes");
    }

    @Test
    void shouldConvertBytesToKilo() {
        assertThat(FileSizeUtils.byteCountToDisplaySize(1024)).isEqualTo("1.0 KB");
        assertThat(FileSizeUtils.byteCountToDisplaySize(1024 + 512)).isEqualTo("1.5 KB");
    }

    @Test
    void shouldOnlyKeepOneDecimal() {
        assertThat(FileSizeUtils.byteCountToDisplaySize(1024 + 512 + 256)).isEqualTo("1.8 KB");
    }

    @Test
    void shouldConvertBytesToMega() {
        assertThat(FileSizeUtils.byteCountToDisplaySize(1024 * 1024)).isEqualTo("1.0 MB");
    }

    @Test
    void shouldConvertBytesToMegaForFloat() {
        assertThat(FileSizeUtils.byteCountToDisplaySize(1024 * 1024 + 512 * 1024)).isEqualTo("1.5 MB");
    }

    @Test
    void shouldConvertBytesToGiga() {
        long twoGiga = 2L * 1024 * 1024 * 1024 + 512 * 1024 * 1024;
        assertThat(FileSizeUtils.byteCountToDisplaySize(twoGiga)).isEqualTo("2.5 GB");
    }

    @Test
    void shouldConvertBytesToTB() {
        long twoGiga = 2L * 1024 * 1024 * 1024 * 1024 + 512L * 1024 * 1024 * 1024;
        assertThat(FileSizeUtils.byteCountToDisplaySize(twoGiga)).isEqualTo("2.5 TB");
    }

    @Test
    void shouldConvertBytesToPB() {
        long twoGiga = 2L * 1024 * 1024 * 1024 * 1024 * 1024 + 512L * 1024 * 1024 * 1024 * 1024;
        assertThat(FileSizeUtils.byteCountToDisplaySize(twoGiga)).isEqualTo("2.5 PB");
    }
}
