package com.doccheck.service;

import com.doccheck.common.BizException;
import com.doccheck.config.UploadSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX 上传前置校验。
 *
 * <p>只检查扩展名不足以阻止把任意 ZIP / 恶意 Office 文件塞进解析器：
 * <ul>
 *   <li>文件头必须是 ZIP 的 PK 标记；</li>
 *   <li>限制 entry 数量、单 entry 解压大小、总解压大小与压缩比；</li>
 *   <li>超限直接拒绝，不落盘、不进入 POI。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocxSecurityValidator {

    private static final int BUFFER = 8192;

    private final UploadSecurityProperties properties;

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的 Word 文档");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".docx")) {
            throw new BizException(415, "仅支持 .docx 格式（不支持旧版 .doc）");
        }

        long fileSize = file.getSize();
        long maxEntry = Math.max(1, properties.getMaxEntryMb()) * 1024 * 1024;
        long maxTotal = Math.max(1, properties.getMaxUncompressedMb()) * 1024 * 1024;

        try (InputStream raw = file.getInputStream()) {
            byte[] magic = raw.readNBytes(4);
            if (magic.length < 2 || magic[0] != 'P' || magic[1] != 'K') {
                throw new BizException(415, "文件内容不是有效的 DOCX（ZIP 文件头缺失）");
            }
        } catch (IOException e) {
            throw new BizException(415, "读取上传文件失败: " + e.getMessage());
        }

        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[BUFFER];
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > Math.max(1, properties.getMaxEntries())) {
                    throw new BizException(413, "DOCX 压缩包条目过多，已拒绝");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                long declared = entry.getSize();
                if (declared > maxEntry) {
                    throw new BizException(413, "DOCX 内单个文件解压后过大，已拒绝");
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > maxEntry) {
                        throw new BizException(413, "DOCX 内单个文件解压后过大，已拒绝");
                    }
                    if (total > maxTotal) {
                        throw new BizException(413, "DOCX 解压后总大小超限，已拒绝");
                    }
                }
            }
        } catch (IOException e) {
            throw new BizException(415, "DOCX 压缩包损坏或无法读取: " + e.getMessage());
        }
        if (entries == 0) {
            throw new BizException(415, "DOCX 压缩包为空");
        }
        long ratioLimit = Math.max(1, properties.getMaxCompressionRatio());
        if (fileSize > 1024 && total > fileSize * ratioLimit) {
            throw new BizException(413, "DOCX 压缩比异常，疑似 Zip 炸弹");
        }
    }
}
