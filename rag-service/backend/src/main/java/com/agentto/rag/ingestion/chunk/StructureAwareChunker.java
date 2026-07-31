package com.agentto.rag.ingestion.chunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructureAwareChunker {

    private static final Pattern SENTENCE = Pattern.compile("[^。！？!?；;\\n]+[。！？!?；;]?|\\n+");
    private static final Pattern CLAUSE = Pattern.compile("[^，、：,:\\n]+[，、：,:]?|\\n+");

    private final int targetChars;
    private final int maxChars;
    private final int overlapChars;

    public StructureAwareChunker(int targetChars, int maxChars, int overlapChars) {
        if (targetChars < 1 || maxChars < targetChars || overlapChars < 0 || overlapChars >= targetChars) {
            throw new IllegalArgumentException("切片参数不合法");
        }
        this.targetChars = targetChars;
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<RagChunk> chunk(List<ParsedBlock> blocks) {
        List<RagChunk> result = new ArrayList<>();
        if (blocks == null) return result;
        List<ParsedBlock> paragraphGroup = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            if (block == null || block.content().isBlank()) continue;
            if (isDocxParagraph(block)) {
                if (!paragraphGroup.isEmpty() && !sameSection(paragraphGroup.get(0), block)) {
                    flushParagraphGroup(result, paragraphGroup);
                }
                if (block.content().length() > maxChars) {
                    flushParagraphGroup(result, paragraphGroup);
                    addPieces(result, block, splitDocxParagraph(block.content()));
                    continue;
                }
                int nextLength = paragraphLength(paragraphGroup)
                        + (paragraphGroup.isEmpty() ? 0 : 1) + block.content().length();
                if (!paragraphGroup.isEmpty() && nextLength > maxChars) {
                    flushParagraphGroup(result, paragraphGroup);
                }
                paragraphGroup.add(block);
                if (paragraphLength(paragraphGroup) >= targetChars) flushParagraphGroup(result, paragraphGroup);
            } else {
                flushParagraphGroup(result, paragraphGroup);
                addPieces(result, block, splitBlock(block.content()));
            }
        }
        flushParagraphGroup(result, paragraphGroup);
        return List.copyOf(result);
    }

    private boolean isDocxParagraph(ParsedBlock block) {
        return "paragraph".equals(block.metadata().get("blockType"));
    }

    private boolean sameSection(ParsedBlock left, ParsedBlock right) {
        return section(left).equals(section(right));
    }

    private String section(ParsedBlock block) {
        return block.metadata().getOrDefault("section", block.title());
    }

    private int paragraphLength(List<ParsedBlock> blocks) {
        if (blocks.isEmpty()) return 0;
        return blocks.stream().mapToInt(block -> block.content().length()).sum() + blocks.size() - 1;
    }

    private void flushParagraphGroup(List<RagChunk> result, List<ParsedBlock> group) {
        if (group.isEmpty()) return;
        ParsedBlock first = group.get(0);
        ParsedBlock last = group.get(group.size() - 1);
        Map<String, String> metadata = new LinkedHashMap<>(first.metadata());
        metadata.put("paragraphStart", paragraphNumber(first, "paragraphStart"));
        metadata.put("paragraphEnd", paragraphNumber(last, "paragraphEnd"));
        String section = section(first);
        if (!section.isBlank()) metadata.put("section", section);
        String content = group.stream().map(ParsedBlock::content).reduce((left, right) -> left + "\n" + right)
                .orElse("");
        result.add(new RagChunk(content, metadata, result.size()));
        group.clear();
    }

    private String paragraphNumber(ParsedBlock block, String key) {
        return block.metadata().getOrDefault(key, block.metadata().getOrDefault("paragraph", ""));
    }

    private void addPieces(List<RagChunk> result, ParsedBlock block, List<String> pieces) {
        for (String content : pieces) {
            if (content.isBlank()) continue;
            Map<String, String> metadata = new LinkedHashMap<>(block.metadata());
            if (!block.title().isBlank()) metadata.putIfAbsent("section", block.title());
            result.add(new RagChunk(content, metadata, result.size()));
        }
    }

    private List<String> splitDocxParagraph(String content) {
        if (content.length() <= maxChars) return List.of(content.trim());
        List<String> units = new ArrayList<>();
        for (String sentence : units(content, SENTENCE)) {
            if (sentence.length() <= maxChars) {
                units.add(sentence);
                continue;
            }
            for (String clause : units(sentence, CLAUSE)) {
                if (clause.length() <= maxChars) units.add(clause);
                else units.addAll(hardParts(clause));
            }
        }
        return packUnits(units);
    }

    private List<String> packUnits(List<String> units) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String previousUnit = "";
        for (int index = 0; index < units.size(); index++) {
            String unit = units.get(index);
            if (!current.isEmpty() && current.length() + unit.length() > maxChars) {
                String completed = current.toString().trim();
                if (!completed.isBlank()) chunks.add(completed);
                current.setLength(0);
                appendCompleteOverlap(current, previousUnit, unit.length());
            }
            current.append(unit);
            previousUnit = unit;
            if (current.length() >= targetChars) {
                String completed = current.toString().trim();
                if (!completed.isBlank()) chunks.add(completed);
                current.setLength(0);
                if (index < units.size() - 1) appendCompleteOverlap(current, previousUnit,
                        units.get(index + 1).length());
            }
        }
        flush(chunks, current);
        return chunks.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private void appendCompleteOverlap(StringBuilder current, String previousUnit, int nextLength) {
        if (!previousUnit.isBlank() && previousUnit.length() <= overlapChars
                && previousUnit.length() + nextLength <= maxChars) {
            current.append(previousUnit);
        }
    }

    private List<String> units(String text, Pattern pattern) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group().trim();
            if (!value.isBlank()) result.add(value);
        }
        if (result.isEmpty()) result.add(text);
        return result;
    }

    private List<String> hardParts(String text) {
        List<String> result = new ArrayList<>();
        splitHard(text, result);
        return result;
    }

    private List<String> splitBlock(String content) {
        if (content.length() <= maxChars) {
            return List.of(content.trim());
        }
        List<String> units = sentenceUnits(content);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (unit.length() > maxChars) {
                flush(chunks, current);
                splitHard(unit, chunks);
                current.setLength(0);
                continue;
            }
            if (!current.isEmpty() && current.length() + unit.length() > maxChars) {
                String previous = current.toString();
                chunks.add(previous.trim());
                current.setLength(0);
                String overlap = tail(previous, overlapChars);
                if (overlap.length() + unit.length() <= maxChars) {
                    current.append(overlap);
                }
            }
            current.append(unit);
            if (current.length() >= targetChars) {
                String completed = current.toString();
                chunks.add(completed.trim());
                current.setLength(0);
                current.append(tail(completed, overlapChars));
            }
        }
        flush(chunks, current);
        return chunks.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private List<String> sentenceUnits(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group().trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        if (result.isEmpty()) {
            result.add(text);
        }
        return result;
    }

    private void splitHard(String text, List<String> target) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            target.add(text.substring(start, end).trim());
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
    }

    private void flush(List<String> chunks, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isBlank()) {
            chunks.add(value);
        }
        current.setLength(0);
    }

    private String tail(String value, int length) {
        if (length == 0 || value.isEmpty()) {
            return "";
        }
        return value.substring(Math.max(0, value.length() - length));
    }
}
