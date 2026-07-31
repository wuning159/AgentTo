package com.agentto.rag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import com.agentto.rag.ingestion.chunk.ParsedBlock;

@Component
public class DocxDocumentParser implements DocumentParser {

    private static final Pattern HEADING_STYLE = Pattern.compile("(?:heading|标题)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream, String filename) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<ParsedBlock> blocks = new ArrayList<>();
            List<String> headingPath = new ArrayList<>();
            int paragraphNo = 0;
            int tableNo = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = normalize(paragraph.getText());
                    if (text.isBlank()) continue;
                    paragraphNo++;
                    int headingLevel = headingLevel(paragraph.getStyle());
                    if (headingLevel > 0) {
                        updateHeadingPath(headingPath, headingLevel, text);
                        continue;
                    }
                    String section = section(headingPath);
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("blockType", "paragraph");
                    metadata.put("paragraph", Integer.toString(paragraphNo));
                    metadata.put("paragraphStart", Integer.toString(paragraphNo));
                    metadata.put("paragraphEnd", Integer.toString(paragraphNo));
                    if (!section.isBlank()) metadata.put("section", section);
                    blocks.add(new ParsedBlock(section, text, metadata));
                } else if (element instanceof XWPFTable table) {
                    tableNo++;
                    String section = section(headingPath);
                    int rowNo = 0;
                    for (XWPFTableRow row : table.getRows()) {
                        rowNo++;
                        String rowText = row.getTableCells().stream()
                                .map(XWPFTableCell::getText)
                                .map(this::normalize)
                                .filter(value -> !value.isBlank())
                                .reduce((left, right) -> left + " | " + right)
                                .orElse("");
                        if (!rowText.isBlank()) {
                            Map<String, String> metadata = new LinkedHashMap<>();
                            metadata.put("blockType", "tableRow");
                            metadata.put("table", Integer.toString(tableNo));
                            metadata.put("rowStart", Integer.toString(rowNo));
                            metadata.put("rowEnd", Integer.toString(rowNo));
                            if (!section.isBlank()) metadata.put("section", section);
                            blocks.add(new ParsedBlock(section, rowText, metadata));
                        }
                    }
                }
            }
            return List.copyOf(blocks);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("DOCX 文档解析失败：" + filename, exception);
        }
    }

    private int headingLevel(String style) {
        if (style == null || style.isBlank()) return 0;
        Matcher matcher = HEADING_STYLE.matcher(style.trim());
        if (!matcher.find()) return 0;
        try { return Math.max(1, Integer.parseInt(matcher.group(1))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void updateHeadingPath(List<String> path, int level, String title) {
        while (path.size() >= level) path.remove(path.size() - 1);
        while (path.size() < level - 1) path.add("");
        path.add(title);
    }

    private String section(List<String> path) {
        return path.stream().filter(value -> !value.isBlank()).reduce((left, right) -> left + " > " + right)
                .orElse("");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
