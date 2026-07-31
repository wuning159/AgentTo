package com.agentto.rag.ingestion.parser;

import java.io.InputStream;
import java.util.List;

import com.agentto.rag.ingestion.chunk.ParsedBlock;

public interface DocumentParser {

    boolean supports(String extension);

    List<ParsedBlock> parse(InputStream inputStream, String filename);
}
