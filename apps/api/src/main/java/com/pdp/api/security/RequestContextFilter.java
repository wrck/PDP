package com.pdp.api.security;

import com.pdp.shared.context.ActorRef;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.OperatorContext;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.context.TraceContext;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ForbiddenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 请求上下文过滤器。
 *
 * <p>从认证主体解析操作者、从 {@code X-Workspace-Id} 头解析工作空间边界，
 * 聚合 {@link OperatorContext}、{@link TraceContext} 与可选 {@link IdempotencyKey} 注入
 * {@link RequestContext}（ThreadLocal），请求结束时清除。
 *
 * <p>强制校验工作空间边界：除公开端点（登录、健康检查）外，所有请求必须携带有效工作空间头。
 * 跨工作空间访问由 {@code AuthorizationService} 在业务层校验协作授权。
 */
@Component
public class RequestContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);
    private static final String WORKSPACE_HEADER = "X-Workspace-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login", "/oauth2", "/actuator/health", "/actuator/info");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        try {
            RequestContext ctx = buildContext(request, isPublic);
            RequestContext.set(ctx);
            response.setHeader("X-Correlation-Id", ctx.trace().correlationId().toString());
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private RequestContext buildContext(HttpServletRequest request, boolean isPublic) {
        // 链路上下文
        UUID traceId = headerUuid(request, TRACE_HEADER, UUID.randomUUID());
        UUID correlationId = headerUuid(request, CORRELATION_HEADER, traceId);
        TraceContext trace = new TraceContext(traceId, correlationId);

        // 操作者（从 Spring Security 认证主体解析）
        ActorRef actor = resolveActor();

        // 工作空间边界（公开端点豁免）
        WorkspaceId workspaceId = null;
        String wsHeader = request.getHeader(WORKSPACE_HEADER);
        if (wsHeader != null && !wsHeader.isBlank()) {
            workspaceId = WorkspaceId.of(wsHeader);
        } else if (!isPublic) {
            throw new ForbiddenException("缺少工作空间边界头: " + WORKSPACE_HEADER);
        }

        // 幂等键（可选，高风险写操作必须携带，由业务层校验）
        IdempotencyKey idempotencyKey = null;
        String idemHeader = request.getHeader(IDEMPOTENCY_HEADER);
        if (idemHeader != null && !idemHeader.isBlank()) {
            idempotencyKey = IdempotencyKey.of(idemHeader);
        }

        OperatorContext operator = new OperatorContext(actor, workspaceId, Set.of(), Set.of());
        return new RequestContext(operator, trace, idempotencyKey);
    }

    private ActorRef resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof OAuth2User oauthUser)) {
            // 未认证请求（公开端点）；使用系统匿名占位
            return ActorRef.system(UUID.randomUUID(), "anonymous");
        }
        String userIdAttr = oauthUser.getAttribute("sub");
        String name = oauthUser.getAttribute("name");
        UUID userId = userIdAttr != null ? UUID.randomUUID() : UUID.randomUUID();
        return ActorRef.user(userId, name != null ? name : "oidc-user");
    }

    private static UUID headerUuid(HttpServletRequest request, String header, UUID fallback) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
