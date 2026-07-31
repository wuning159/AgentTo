package com.agentto.rag.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 调用方应用管理 REST 控制器。
 * 提供创建调用方应用、生成和撤销 API Key 的管理端点。
 *
 * 端点：
 * POST /api/admin/clients                           创建调用方应用
 * POST /api/admin/clients/{appUid}/keys            生成 API Key
 * POST /api/admin/clients/{appUid}/keys/{keyPrefix}/revoke  撤销 API Key
 */
@RestController
@RequestMapping("/api/admin/clients")
public class ClientApplicationAdminController {

    private final ClientApplicationAdminService adminService;

    public ClientApplicationAdminController(ClientApplicationAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 创建调用方应用。
     */
    @PostMapping
    public ApiResponse<ClientView> createClient(@RequestBody CreateClientRequest request,
            HttpServletRequest httpRequest) {
        ClientApplication app = adminService.createClient(request.appUid(), request.name());
        return ApiResponse.ok(ClientView.from(app), TraceIdFilter.current(httpRequest));
    }

    /**
     * 生成 API Key。完整密钥仅在此响应中返回一次。
     */
    @PostMapping("/{appUid}/keys")
    public ApiResponse<CreatedClientApiKey> createApiKey(@PathVariable String appUid,
            HttpServletRequest httpRequest) {
        CreatedClientApiKey result = adminService.createApiKey(appUid);
        return ApiResponse.ok(result, TraceIdFilter.current(httpRequest));
    }

    /**
     * 撤销 API Key。
     */
    @PostMapping("/{appUid}/keys/{keyPrefix}/revoke")
    public ApiResponse<Void> revokeApiKey(@PathVariable String appUid, @PathVariable String keyPrefix,
            HttpServletRequest httpRequest) {
        adminService.revokeApiKey(appUid, keyPrefix);
        return ApiResponse.ok(null, TraceIdFilter.current(httpRequest));
    }

    /** 创建调用方请求体 */
    public record CreateClientRequest(String appUid, String name) {
    }

    /** 调用方应用视图 */
    public record ClientView(String appUid, String name, String status,
            java.time.Instant createdAt, java.time.Instant updatedAt) {

        static ClientView from(ClientApplication app) {
            return new ClientView(app.getAppUid(), app.getName(), app.getStatus(),
                    app.getCreatedAt(), app.getUpdatedAt());
        }
    }
}
