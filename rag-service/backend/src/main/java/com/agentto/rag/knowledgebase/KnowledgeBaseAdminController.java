package com.agentto.rag.knowledgebase;

import java.time.Instant;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 知识库管理 REST 控制器。
 * 提供知识库创建、画像更新和共享授权管理端点。
 *
 * 端点：
 * POST   /api/admin/knowledge-bases                   创建知识库
 * PUT    /api/admin/knowledge-bases/{kbUid}/profile    更新画像
 * POST   /api/admin/knowledge-bases/{kbUid}/grants     添加授权
 * DELETE /api/admin/knowledge-bases/{kbUid}/grants/{appUid}  移除授权
 */
@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class KnowledgeBaseAdminController {

    private final KnowledgeBaseAdminService adminService;

    public KnowledgeBaseAdminController(KnowledgeBaseAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 创建知识库。
     */
    @PostMapping
    public ApiResponse<KnowledgeBaseView> create(@RequestBody CreateKnowledgeBaseRequest request,
            HttpServletRequest httpRequest) {
        KnowledgeBase created = adminService.createKnowledgeBase(
                request.name(), request.description(), request.visibility(), request.ownerAppId());
        return ApiResponse.ok(KnowledgeBaseView.from(created), TraceIdFilter.current(httpRequest));
    }

    /**
     * 更新知识库画像。
     */
    @PutMapping("/{kbUid}/profile")
    public ApiResponse<KnowledgeBaseView> updateProfile(@PathVariable String kbUid,
            @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
        KnowledgeBase updated = adminService.updateProfile(kbUid, request.description());
        return ApiResponse.ok(KnowledgeBaseView.from(updated), TraceIdFilter.current(httpRequest));
    }

    /**
     * 添加共享授权。
     */
    @PostMapping("/{kbUid}/grants")
    public ApiResponse<Void> addGrant(@PathVariable String kbUid, @RequestBody AddGrantRequest request,
            HttpServletRequest httpRequest) {
        adminService.addGrant(kbUid, request.appUid());
        return ApiResponse.ok(null, TraceIdFilter.current(httpRequest));
    }

    /**
     * 移除共享授权。
     */
    @DeleteMapping("/{kbUid}/grants/{appUid}")
    public ApiResponse<Void> removeGrant(@PathVariable String kbUid, @PathVariable String appUid,
            HttpServletRequest httpRequest) {
        adminService.removeGrant(kbUid, appUid);
        return ApiResponse.ok(null, TraceIdFilter.current(httpRequest));
    }

    /** 创建知识库请求体 */
    public record CreateKnowledgeBaseRequest(String name, String description, String visibility, Long ownerAppId) {
    }

    /** 更新画像请求体 */
    public record UpdateProfileRequest(String description) {
    }

    /** 添加授权请求体 */
    public record AddGrantRequest(String appUid) {
    }

    /** 知识库视图 */
    public record KnowledgeBaseView(String kbUid, String name, String description, String visibility,
            Long ownerAppId, String status, int profileVersion, Instant createdAt, Instant updatedAt) {

        static KnowledgeBaseView from(KnowledgeBase kb) {
            return new KnowledgeBaseView(kb.getKbUid(), kb.getName(), kb.getDescription(),
                    kb.isShared() ? "SHARED" : "PRIVATE", kb.getOwnerAppId(),
                    kb.isActive() ? "ACTIVE" : "DISABLED", kb.getProfileVersion(),
                    kb.getCreatedAt(), kb.getUpdatedAt());
        }
    }
}
