package com.agentto.rag.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 评测数据集加载器：加载 JSONL 数据集，每行一个用例。
 *
 * <p>校验规则：
 * <ul>
 *   <li>用例 ID 重复 → 拒绝（IllegalArgumentException）</li>
 *   <li>查询为空 / 期望决策缺失 → 拒绝</li>
 *   <li>期望决策为未知枚举值 → 拒绝（格式非法）</li>
 *   <li>空行跳过，保持文件行序</li>
 * </ul>
 */
@Component
public class EvaluationDatasetLoader {

    /** 默认基线数据集路径（classpath） */
    public static final String DEFAULT_DATASET_PATH = "rag-eval/baseline.jsonl";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * 构造加载器。
     *
     * @param objectMapper   JSON 解析器
     * @param resourceLoader 资源加载器（classpath 或 file 位置）
     */
    public EvaluationDatasetLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载默认基线数据集。
     *
     * @return 评测用例列表（保持行序）
     */
    public List<EvaluationCase> loadDefault() {
        return load("classpath:" + DEFAULT_DATASET_PATH);
    }

    /**
     * 加载指定位置的数据集。
     *
     * @param location 资源位置（如 classpath:rag-eval/baseline.jsonl 或 file:…）
     * @return 评测用例列表（保持行序）
     */
    public List<EvaluationCase> load(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("评测数据集不存在：" + location);
        }
        List<EvaluationCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try (InputStream input = resource.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                EvaluationCase testCase = parseLine(line, lineNo);
                if (!ids.add(testCase.id())) {
                    throw new IllegalArgumentException("评测用例 ID 重复：" + testCase.id()
                            + "（第 " + lineNo + " 行）");
                }
                cases.add(testCase);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取评测数据集失败：" + location, e);
        }
        return List.copyOf(cases);
    }

    /**
     * 解析单行 JSON 为评测用例。
     *
     * @param line   JSONL 行
     * @param lineNo 行号（用于错误提示）
     * @return 评测用例
     */
    private EvaluationCase parseLine(String line, int lineNo) {
        try {
            return objectMapper.readValue(line, EvaluationCase.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("评测数据集第 " + lineNo + " 行格式非法："
                    + e.getOriginalMessage(), e);
        }
    }
}
