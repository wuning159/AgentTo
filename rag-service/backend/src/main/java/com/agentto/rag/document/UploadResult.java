package com.agentto.rag.document;

public record UploadResult(Long documentId, Long versionId, Long jobId, String objectKey,
        boolean duplicate, String message) {

    public static UploadResult created(Long documentId, Long versionId, Long jobId, String objectKey) {
        return new UploadResult(documentId, versionId, jobId, objectKey, false, "文件已进入处理队列");
    }

    public static UploadResult duplicate(RagDocumentVersion version) {
        return new UploadResult(version.getDocumentId(), version.getId(), null, version.getObjectKey(), true,
                "该文件已经入库，已为你打开已有文档");
    }
}
