package com.agentto.rag.ingestion.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DocumentParserFactory {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of("docx", "pdf", "xlsx", "xls");

    private final Map<String, DocumentParser> parserByExtension = new LinkedHashMap<>();

    public DocumentParserFactory(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            for (String extension : SUPPORTED_EXTENSIONS) {
                if (parser.supports(extension)) {
                    parserByExtension.put(extension, parser);
                }
            }
        }
    }

    public DocumentParser forFile(String filename) {
        String extension = extension(filename);
        DocumentParser parser = parserByExtension.get(extension);
        if (parser == null) {
            throw new IllegalArgumentException("不支持的文档类型：" + extension);
        }
        return parser;
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        int separator = filename.lastIndexOf('.');
        if (separator < 0 || separator == filename.length() - 1) {
            throw new IllegalArgumentException("文件缺少扩展名：" + filename);
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
