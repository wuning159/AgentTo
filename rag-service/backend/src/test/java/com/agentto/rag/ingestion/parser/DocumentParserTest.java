package com.agentto.rag.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import com.agentto.rag.ingestion.chunk.ParsedBlock;

class DocumentParserTest {

    @Test
    void docxExtractsParagraphsAndTableRows() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("制度正文第一段");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("部门");
            table.getRow(0).getCell(1).setText("职责");
            table.getRow(1).getCell(0).setText("财务部");
            table.getRow(1).getCell(1).setText("预算审查");
            document.write(output);
            bytes = output.toByteArray();
        }

        List<ParsedBlock> blocks = new DocxDocumentParser()
                .parse(new ByteArrayInputStream(bytes), "制度.docx");

        assertThat(blocks).extracting(ParsedBlock::content)
                .anyMatch(text -> text.contains("制度正文第一段"))
                .anyMatch(text -> text.contains("财务部") && text.contains("预算审查"));
    }

    @Test
    void docxPreservesParagraphAndTableBodyOrder() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("表格前正文");
            document.createTable(1, 1).getRow(0).getCell(0).setText("中间表格");
            document.createParagraph().createRun().setText("表格后正文");
            document.write(output);
            bytes = output.toByteArray();
        }

        List<ParsedBlock> blocks = new DocxDocumentParser()
                .parse(new ByteArrayInputStream(bytes), "顺序.docx");

        assertThat(blocks).extracting(ParsedBlock::content)
                .containsExactly("表格前正文", "中间表格", "表格后正文");
        assertThat(blocks).extracting(block -> block.metadata().get("blockType"))
                .containsExactly("paragraph", "tableRow", "paragraph");
    }

    @Test
    void docxBuildsHierarchicalHeadingPathForBodyParagraphs() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            heading(document, "第一章", "Heading1");
            document.createParagraph().createRun().setText("一级正文");
            heading(document, "审批管理", "Heading2");
            document.createParagraph().createRun().setText("二级正文");
            heading(document, "第二章", "Heading1");
            document.createParagraph().createRun().setText("新的一级正文");
            document.write(output);
            bytes = output.toByteArray();
        }

        List<ParsedBlock> blocks = new DocxDocumentParser()
                .parse(new ByteArrayInputStream(bytes), "标题.docx");

        assertThat(blocks).extracting(ParsedBlock::content)
                .containsExactly("一级正文", "二级正文", "新的一级正文");
        assertThat(blocks).extracting(block -> block.metadata().get("section"))
                .containsExactly("第一章", "第一章 > 审批管理", "第二章");
    }

    private void heading(XWPFDocument document, String text, String style) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(style);
        paragraph.createRun().setText(text);
    }

    @Test
    void pdfPreservesPageNumber() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("AgentTo policy page one");
                stream.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }

        List<ParsedBlock> blocks = new PdfDocumentParser()
                .parse(new ByteArrayInputStream(bytes), "policy.pdf");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).content()).contains("AgentTo policy page one");
        assertThat(blocks.get(0).metadata()).containsEntry("page", "1");
    }

    @Test
    void excelPreservesSheetAndRowAndUsesHeaderNames() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("职责清单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("部门");
            header.createCell(1).setCellValue("职责");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("法务部");
            row.createCell(1).setCellValue("合同审查");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        List<ParsedBlock> blocks = new ExcelDocumentParser()
                .parse(new ByteArrayInputStream(bytes), "职责.xlsx");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).content()).contains("部门：法务部", "职责：合同审查");
        assertThat(blocks.get(0).metadata())
                .containsEntry("sheet", "职责清单")
                .containsEntry("rowStart", "2")
                .containsEntry("rowEnd", "2");
    }

    @Test
    void factoryRejectsUnsupportedFileType() {
        DocumentParserFactory factory = new DocumentParserFactory(List.of(
                new DocxDocumentParser(), new PdfDocumentParser(), new ExcelDocumentParser()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.forFile("archive.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }
}
