package com.intelligence.platform.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 项目上下文服务（参考 llm_wiki 的数据隔离机制）
 * 每个请求携带当前项目ID，所有数据操作按 project_id 过滤
 */
@Service
public class ProjectContext {

    /**
     * 从请求头获取当前项目ID
     * 前端通过 X-Project-Id 头传递
     * 如果未传递，返回 null（表示全局/未绑定项目）
     */
    private static final ThreadLocal<Long> overrideProjectId = new ThreadLocal<>();

    public void setCurrentProjectId(Long id) {
        if (id == null) {
            overrideProjectId.remove();
        } else {
            overrideProjectId.set(id);
        }
    }

    public void clearCurrentProjectId() {
        overrideProjectId.remove();
    }

    public Long getCurrentProjectId() {
        if (overrideProjectId.get() != null) {
            return overrideProjectId.get();
        }
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String projectId = request.getHeader("X-Project-Id");
            if (projectId == null || projectId.isEmpty()) {
                // 也支持查询参数
                projectId = request.getParameter("projectId");
            }
            if (projectId != null && !projectId.isEmpty()) {
                return Long.parseLong(projectId);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 校验实体是否属于当前项目，抛出异常如果项目不匹配
     * @param entityProjectId 实体的 project_id 字段值
     * @param entityType 实体类型名称（用于错误消息）
     * @throws org.springframework.web.server.ResponseStatusException 如果项目不匹配
     */
    public void validateProjectAccess(Long entityProjectId, String entityType) {
        Long currentProjectId = getCurrentProjectId();
        if (currentProjectId == null) return; // 全局模式，允许访问
        if (entityProjectId != null && !entityProjectId.equals(currentProjectId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                entityType + " 不属于当前项目"
            );
        }
    }

    /**
     * 校验实体是否属于当前项目
     * @param entityProjectId 实体的 project_id 字段值
     * @return true 如果允许访问
     */
    public boolean canAccess(Long entityProjectId) {
        Long currentProjectId = getCurrentProjectId();
        if (currentProjectId == null) return true;
        return entityProjectId == null || entityProjectId.equals(currentProjectId);
    }
}
