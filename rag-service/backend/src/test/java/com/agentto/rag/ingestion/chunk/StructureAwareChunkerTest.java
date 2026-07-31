package com.agentto.rag.ingestion.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StructureAwareChunkerTest {

    @Test
    void preservesSourceLocationAndNeverCreatesBlankChunks() {
        List<ParsedBlock> blocks = List.of(
                new ParsedBlock("第一章", "第一段内容。第二句内容。", Map.of("page", "1", "section", "第一章")),
                new ParsedBlock("第一章", "   ", Map.of("page", "1")),
                new ParsedBlock("第一章", "第三段内容。", Map.of("page", "2", "section", "第一章")));

        List<RagChunk> chunks = new StructureAwareChunker(20, 32, 4).chunk(blocks);

        assertThat(chunks).isNotEmpty().allMatch(chunk -> !chunk.content().isBlank());
        assertThat(chunks.get(0).metadata()).containsEntry("section", "第一章");
        assertThat(chunks).extracting(RagChunk::ordinal).containsExactly(0, 1);
    }

    @Test
    void splitsLongTextAtSentenceBoundariesWithinMaximumLength() {
        String text = "甲乙丙丁戊己庚辛。壬癸子丑寅卯辰巳。午未申酉戌亥天地玄黄。宇宙洪荒日月盈昃。";
        ParsedBlock block = new ParsedBlock("测试", text, Map.of("page", "3"));

        List<RagChunk> chunks = new StructureAwareChunker(18, 24, 4).chunk(List.of(block));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 24);
        assertThat(chunks.get(0).metadata()).containsEntry("page", "3");
    }

    @Test
    void combinesAdjacentDocxParagraphsInsideTheSameSection() {
        List<ParsedBlock> blocks = List.of(
                paragraph("第一章", "第一段内容。", 1),
                paragraph("第一章", "第二段内容。", 2),
                paragraph("第一章", "第三段内容。", 3));

        List<RagChunk> chunks = new StructureAwareChunker(18, 32, 4).chunk(blocks);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("第一段内容。", "第二段内容。", "第三段内容。");
        assertThat(chunks.get(0).metadata())
                .containsEntry("section", "第一章")
                .containsEntry("paragraphStart", "1")
                .containsEntry("paragraphEnd", "3");
    }

    @Test
    void neverCombinesDocxParagraphsAcrossSections() {
        List<ParsedBlock> blocks = List.of(
                paragraph("第一章", "第一章短段落。", 1),
                paragraph("第二章", "第二章短段落。", 2));

        List<RagChunk> chunks = new StructureAwareChunker(30, 40, 4).chunk(blocks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(chunk -> chunk.metadata().get("section"))
                .containsExactly("第一章", "第二章");
    }

    @Test
    void splitsAnOversizedSentenceAtSecondaryPunctuationBeforeHardCutting() {
        ParsedBlock block = paragraph("第一章", "甲乙丙丁，戊己庚辛，壬癸子丑，寅卯辰巳。", 1);

        List<RagChunk> chunks = new StructureAwareChunker(10, 14, 0).chunk(List.of(block));

        assertThat(chunks).hasSizeGreaterThan(1).allMatch(chunk -> chunk.content().length() <= 14);
        assertThat(chunks.subList(0, chunks.size() - 1))
                .allMatch(chunk -> chunk.content().matches(".*[，、：。！？；]$"));
    }

    @Test
    void keepsPdfPagesAndExcelRowsAsIndependentBlocks() {
        List<ParsedBlock> blocks = List.of(
                new ParsedBlock("第 1 页", "PDF第一页", Map.of("blockType", "page", "page", "1")),
                new ParsedBlock("第 2 页", "PDF第二页", Map.of("blockType", "page", "page", "2")),
                new ParsedBlock("预算表", "部门：财务部", Map.of("blockType", "sheetRow", "sheet", "预算表", "rowStart", "2")),
                new ParsedBlock("预算表", "部门：法务部", Map.of("blockType", "sheetRow", "sheet", "预算表", "rowStart", "3")));

        List<RagChunk> chunks = new StructureAwareChunker(30, 40, 4).chunk(blocks);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(RagChunk::content)
                .containsExactly("PDF第一页", "PDF第二页", "部门：财务部", "部门：法务部");
    }

    private ParsedBlock paragraph(String section, String content, int number) {
        return new ParsedBlock(section, content, Map.of(
                "blockType", "paragraph",
                "section", section,
                "paragraphStart", Integer.toString(number),
                "paragraphEnd", Integer.toString(number)));
    }
}
