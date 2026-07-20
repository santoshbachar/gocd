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
package com.thoughtworks.go.domain;

import com.thoughtworks.go.util.SystemEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class ChecksumFileHandlerTest {

    private File file;
    private ChecksumFileHandler checksumFileHandler;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        file = Files.createTempFile(tempDir, "checksum", null).toFile();
        checksumFileHandler = new ChecksumFileHandler(file);
        new SystemEnvironment().set(SystemEnvironment.SERVICE_URL, "http://foo/go");
    }

    @AfterEach
    void tearDown() {
        new SystemEnvironment().clearProperty(SystemEnvironment.SERVICE_URL.propertyName());
    }

    @Test
    void shouldGenerateChecksumFileUrl() {
        String url = checksumFileHandler.url("cruise/1/stage/1/job");
        assertThat(url).isEqualTo("http://foo/go/remoting/files/cruise/1/stage/1/job/cruise-output/md5.checksum");
    }

    @Test
    void shouldStoreTheMd5ChecksumOnTheAgent() throws IOException {
        checksumFileHandler.handle(new ByteArrayInputStream("Hello World".getBytes()));
        assertThat(Files.readString(file.toPath(), UTF_8)).isEqualTo("Hello World");
    }

    @Test
    void shouldDeleteOldMd5ChecksumFileIfItWasNotFoundOnTheServer() throws IOException {
        StubGoPublisher goPublisher = new StubGoPublisher();
        file.createNewFile();

        boolean isSuccessful = checksumFileHandler.handleResult(HttpURLConnection.HTTP_NOT_FOUND, goPublisher);
        assertThat(isSuccessful).isTrue();
        assertThat(file.exists()).isFalse();
    }

    @Test
    void shouldRetainMd5ChecksumFileIfItIsDownloadedSuccessfully() throws IOException {
        StubGoPublisher goPublisher = new StubGoPublisher();
        file.createNewFile();

        boolean isSuccessful = checksumFileHandler.handleResult(HttpURLConnection.HTTP_OK, goPublisher);
        assertThat(isSuccessful).isTrue();
        assertThat(file.exists()).isTrue();

    }

    @Test
    void shouldHandleResultIfHttpCodeSaysFileNotFound() {
        StubGoPublisher goPublisher = new StubGoPublisher();
        assertThat(checksumFileHandler.handleResult(HttpURLConnection.HTTP_NOT_FOUND, goPublisher)).isTrue();
        assertThat(goPublisher.getMessage()).contains("[WARN] The md5checksum property file was not found on the server. Hence, Go can not verify the integrity of the artifacts.");
    }

    @Test
    void shouldHandleResultIfHttpCodeIsSuccessful() {
        StubGoPublisher goPublisher = new StubGoPublisher();
        assertThat(checksumFileHandler.handleResult(HttpURLConnection.HTTP_OK, goPublisher)).isTrue();
    }

    @Test
    void shouldHandleResultIfHttpCodeSaysFileNotModified() {
        StubGoPublisher goPublisher = new StubGoPublisher();
        assertThat(checksumFileHandler.handleResult(HttpURLConnection.HTTP_NOT_MODIFIED, goPublisher)).isTrue();
    }

    @Test
    void shouldHandleResultIfHttpCodeSaysFilePermissionDenied() {
        StubGoPublisher goPublisher = new StubGoPublisher();
        assertThat(checksumFileHandler.handleResult(HttpURLConnection.HTTP_FORBIDDEN, goPublisher)).isFalse();
    }

    @Test
    void shouldGetArtifactMd5Checksum() throws IOException {
        checksumFileHandler.handle(new ByteArrayInputStream("Hello!!!1".getBytes()));
        ArtifactMd5Checksums artifactMd5Checksums = checksumFileHandler.getArtifactMd5Checksums();
        assertThat(artifactMd5Checksums).isEqualTo(new ArtifactMd5Checksums(file));
    }

    @Test
    void shouldReturnNullArtifactMd5ChecksumIfFileDoesNotExist() {
        file.delete();
        assertThat(checksumFileHandler.getArtifactMd5Checksums()).isNull();
    }


}
