package com.agentto.rag.asset;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

import org.springframework.web.multipart.MultipartFile;

public final class UploadedDocument {

    private final String filename;
    private final String contentType;
    private final long contentLength;
    private final Supplier<InputStream> opener;

    public UploadedDocument(String filename, String contentType, long contentLength, Supplier<InputStream> opener) {
        if (contentLength < 0) {
            throw new IllegalArgumentException("上传内容长度非法");
        }
        this.filename = filename;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.opener = opener;
    }

    public static UploadedDocument from(MultipartFile file) {
        return new UploadedDocument(file.getOriginalFilename(), file.getContentType(), file.getSize(), () -> {
            try {
                return file.getInputStream();
            } catch (IOException exception) {
                throw new IllegalArgumentException("读取上传文件失败", exception);
            }
        });
    }

    public static UploadedDocument of(String filename, String contentType, byte[] bytes) {
        byte[] copy = bytes.clone();
        return new UploadedDocument(filename, contentType, copy.length, () -> new ByteArrayInputStream(copy));
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public long contentLength() {
        return contentLength;
    }

    public InputStream openStream() {
        InputStream stream = opener.get();
        if (stream == null) {
            throw new IllegalArgumentException("读取上传文件失败");
        }
        return stream;
    }
}
