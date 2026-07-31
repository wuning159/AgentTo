package com.agentto.rag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import com.agentto.rag.ingestion.chunk.ParsedBlock;

@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "xlsx".equalsIgnoreCase(extension) || "xls".equalsIgnoreCase(extension);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream, String filename) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<ParsedBlock> blocks = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet sheet : workbook) {
                List<String> headers = headers(sheet, formatter, evaluator);
                int firstDataRow = Math.max(sheet.getFirstRowNum() + 1, 1);
                for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    String content = rowContent(row, headers, formatter, evaluator);
                    if (!content.isBlank()) {
                        Map<String, String> metadata = new LinkedHashMap<>();
                        metadata.put("blockType", "sheetRow");
                        metadata.put("sheet", sheet.getSheetName());
                        metadata.put("rowStart", Integer.toString(rowIndex + 1));
                        metadata.put("rowEnd", Integer.toString(rowIndex + 1));
                        blocks.add(new ParsedBlock(sheet.getSheetName(), content, metadata));
                    }
                }
            }
            return List.copyOf(blocks);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Excel 文档解析失败：" + filename, exception);
        }
    }

    private List<String> headers(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            return List.of();
        }
        int lastCell = Math.max(header.getLastCellNum(), 0);
        List<String> result = new ArrayList<>(lastCell);
        for (int column = 0; column < lastCell; column++) {
            String value = value(header.getCell(column), formatter, evaluator);
            result.add(value.isBlank() ? "列" + (column + 1) : value);
        }
        return result;
    }

    private String rowContent(Row row, List<String> headers, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return "";
        }
        int columns = Math.max(headers.size(), Math.max(row.getLastCellNum(), 0));
        List<String> cells = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            String cellValue = value(row.getCell(column), formatter, evaluator);
            if (!cellValue.isBlank()) {
                String header = column < headers.size() ? headers.get(column) : "列" + (column + 1);
                cells.add(header + "：" + cellValue);
            }
        }
        return String.join("；", cells);
    }

    private String value(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
