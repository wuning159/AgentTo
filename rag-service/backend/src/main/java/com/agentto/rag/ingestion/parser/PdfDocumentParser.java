package com.agentto.rag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.agentto.rag.ingestion.chunk.ParsedBlock;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream, String filename) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            List<ParsedBlock> blocks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .trim();
                if (!text.isBlank()) {
                    blocks.add(new ParsedBlock("第 " + page + " 页", text,
                            Map.of("blockType", "page", "page", Integer.toString(page))));
                }
            }
            return List.copyOf(blocks);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("PDF 文档解析失败：" + filename, exception);
        }
    }
}
