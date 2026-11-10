package com.doccheck.service;

import com.doccheck.common.BizException;
import com.doccheck.config.UploadSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocxSecurityValidatorTest {

    private DocxSecurityValidator validator(int maxEntries, int maxRatio) {
        UploadSecurityProperties props = new UploadSecurityProperties();
        props.setMaxEntries(maxEntries);
        props.setMaxCompressionRatio(maxRatio);
        props.setMaxUncompressedMb(10);
        props.setMaxEntryMb(5);
        return new DocxSecurityValidator(props);
    }

    @Test
    void acceptsMinimalDocxZip() {
        byte[] bytes = zip(new byte[]{1, 2, 3}, new byte[]{4, 5});
        MockMultipartFile file = new MockMultipartFile("file", "ok.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
        assertDoesNotThrow(() -> validator(10, 120).validate(file));
    }

    @Test
    void rejectsWrongExtensionAndMissingZipMagic() {
        MockMultipartFile doc = new MockMultipartFile("file", "ok.doc", "application/msword", new byte[]{1});
        BizException e1 = assertThrows(BizException.class, () -> validator(10, 120).validate(doc));
        assertEquals(415, e1.getCode());

        MockMultipartFile fake = new MockMultipartFile("file", "fake.docx", "application/octet-stream",
                "not a zip".getBytes(StandardCharsets.UTF_8));
        BizException e2 = assertThrows(BizException.class, () -> validator(10, 120).validate(fake));
        assertEquals(415, e2.getCode());
    }

    @Test
    void rejectsTooManyEntries() {
        byte[] bytes = zip(new byte[]{1}, new byte[]{2}, new byte[]{3});
        MockMultipartFile file = new MockMultipartFile("file", "many.docx", "application/octet-stream", bytes);
        BizException e = assertThrows(BizException.class, () -> validator(2, 120).validate(file));
        assertEquals(413, e.getCode());
    }

    @Test
    void rejectsSuspiciousCompressionRatio() {
        byte[] zeros = new byte[1024 * 1024];
        byte[] random = new byte[8192];
        new Random(7).nextBytes(random);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(zeros);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/media/image1.png"));
            zip.write(random);
            zip.closeEntry();
        } catch (Exception ignored) {
            throw new IllegalStateException(ignored);
        }
        MockMultipartFile file = new MockMultipartFile("file", "bomb.docx", "application/octet-stream", out.toByteArray());
        BizException e = assertThrows(BizException.class, () -> validator(100, 20).validate(file));
        assertEquals(413, e.getCode());
    }

    private byte[] zip(byte[]... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < entries.length; i++) {
                zip.putNextEntry(new ZipEntry("part" + i + ".xml"));
                zip.write(entries[i]);
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
