package com.agentto.rag.document;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agentto.rag.auth.AuthRequestContext;
import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.PageResult;
import com.agentto.rag.common.api.TraceIdFilter;
import com.agentto.rag.ingestion.ChunkView;
import com.agentto.rag.ingestion.IngestionJobView;
import com.agentto.rag.ingestion.IngestionLauncher;
import com.agentto.rag.ingestion.IngestionQueryService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
public class DocumentAdminController {

    private final DocumentService documentService;
    private final DocumentQueryService documentQueryService;
    private final IngestionLauncher ingestionLauncher;
    private final IngestionQueryService ingestionQueryService;

    public DocumentAdminController(DocumentService documentService, DocumentQueryService documentQueryService,
            IngestionLauncher ingestionLauncher, IngestionQueryService ingestionQueryService) {
        this.documentService = documentService;
        this.documentQueryService = documentQueryService;
        this.ingestionLauncher = ingestionLauncher;
        this.ingestionQueryService = ingestionQueryService;
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ApiResponse<UploadResult> upload(@RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String category, HttpServletRequest request) {
        UploadResult result = documentService.upload(file, category, AuthRequestContext.principal(request).userId());
        if (result.jobId() != null) {
            ingestionLauncher.launch(result.jobId());
        }
        return ApiResponse.ok(result, TraceIdFilter.current(request));
    }

    @GetMapping("/documents")
    public ApiResponse<PageResult<DocumentSummary>> documents(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return ApiResponse.ok(PageResult.from(documentQueryService.list(page, size)), TraceIdFilter.current(request));
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentDetail> document(@PathVariable Long documentId, HttpServletRequest request) {
        return ApiResponse.ok(documentQueryService.detail(documentId), TraceIdFilter.current(request));
    }

    @GetMapping("/versions/{versionId}/chunks")
    public ApiResponse<PageResult<ChunkView>> chunks(@PathVariable Long versionId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.ok(PageResult.from(ingestionQueryService.chunks(versionId, page, size)),
                TraceIdFilter.current(request));
    }

    @GetMapping("/versions/{versionId}/ingestion")
    public ApiResponse<IngestionJobView> latestIngestion(@PathVariable Long versionId,
            HttpServletRequest request) {
        return ApiResponse.ok(ingestionQueryService.latestForVersion(versionId),
                TraceIdFilter.current(request));
    }

    @GetMapping("/ingestion/jobs/{jobId}")
    public ApiResponse<IngestionJobView> job(@PathVariable Long jobId, HttpServletRequest request) {
        return ApiResponse.ok(ingestionQueryService.job(jobId), TraceIdFilter.current(request));
    }
}
