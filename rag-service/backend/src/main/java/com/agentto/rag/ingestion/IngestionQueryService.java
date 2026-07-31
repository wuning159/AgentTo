package com.agentto.rag.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.common.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IngestionQueryService {

    private final IngestionJobRepository jobRepository;
    private final IngestionStageRepository stageRepository;
    private final RagChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    public IngestionQueryService(IngestionJobRepository jobRepository, IngestionStageRepository stageRepository,
            RagChunkRepository chunkRepository, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.stageRepository = stageRepository;
        this.chunkRepository = chunkRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public IngestionJobView job(Long jobId) {
        IngestionJob value = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "入库任务不存在", HttpStatus.NOT_FOUND));
        return view(value);
    }

    @Transactional(readOnly = true)
    public IngestionJobView latestForVersion(Long versionId) {
        IngestionJob value = jobRepository.findFirstByVersionIdOrderByCreatedAtDesc(versionId)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "该版本还没有入库任务",
                        HttpStatus.NOT_FOUND));
        return view(value);
    }

    private IngestionJobView view(IngestionJob value) {
        return new IngestionJobView(value.getId(), value.getDocumentId(), value.getVersionId(), value.getStatus(),
                value.getCurrentStage(), value.getAttemptNo(), value.getErrorCode(), value.getErrorMessage(),
                value.getStartedAt(), value.getFinishedAt(), value.getCreatedAt(),
                stageRepository.findByJobIdOrderById(value.getId()).stream()
                        .map(stage -> IngestionStageView.from(stage, objectMapper)).toList());
    }

    @Transactional(readOnly = true)
    public Page<ChunkView> chunks(Long versionId, int page, int size) {
        return chunkRepository.findByVersionIdOrderByOrdinalNo(versionId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(ChunkView::from);
    }
}
