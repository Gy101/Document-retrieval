package com.doccheck.service;

import com.doccheck.service.storage.FileHashes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileHashesTest {

    @Test
    void computesKnownSha256() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt",
                "text/plain", "abc".getBytes(StandardCharsets.UTF_8));
        assertThat(FileHashes.sha256(file))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sameContentHasSameFingerprint() {
        MockMultipartFile a = new MockMultipartFile("file", "a.docx",
                "application/octet-stream", "same".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile b = new MockMultipartFile("file", "b.docx",
                "application/octet-stream", "same".getBytes(StandardCharsets.UTF_8));
        assertThat(FileHashes.sha256(a)).isEqualTo(FileHashes.sha256(b));
    }
}
