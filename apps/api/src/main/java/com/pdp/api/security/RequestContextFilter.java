package com.pdp.api.security;

import com.pdp.identity.application.WorkspaceBoundaryVerifier;
import com.pdp.identity.application.AuthenticatedActor;
import com.pdp.shared.context.ActorId;
import com.pdp.shared.context.IdempotencyKey;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.context.TraceId;
import com.pdp.shared.context.WorkspaceId;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.PdpException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从认证主体与稳定请求头构建请求上下文，并在进入业务处理前校验工作空间边界。
 *
 * <p>工作空间列表、OIDC 回调和运维探针不要求工作空间头；其余 API 默认强制要求。
 */
public final class RequestContextFilter extends OncePerRequestFilter {
  public static final String ATTRIBUTE = RequestContext.class.getName();
  public static final String WORKSPACE_HEADER = "X-Workspace-Id";
  public static final String TRACE_HEADER = "X-Trace-Id";
  public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private final WorkspaceBoundaryVerifier boundaryVerifier;

  public RequestContextFilter(WorkspaceBoundaryVerifier boundaryVerifier) {
    this.boundaryVerifier = boundaryVerifier;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new PdpException(ErrorCode.AUTHENTICATION_REQUIRED, "请求缺少已认证操作者");
    }
    ActorId actorId = new ActorId(resolveActorId(authentication));
    WorkspaceId workspaceId =
        new WorkspaceId(parseUuid(request.getHeader(WORKSPACE_HEADER), "工作空间标识"));
    TraceId traceId =
        new TraceId(
            Optional.ofNullable(request.getHeader(TRACE_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString()));
    Optional<IdempotencyKey> idempotencyKey =
        Optional.ofNullable(request.getHeader(IDEMPOTENCY_HEADER))
            .filter(value -> !value.isBlank())
            .map(IdempotencyKey::new);
    boundaryVerifier.requireWorkspaceAccess(actorId, workspaceId);
    request.setAttribute(
        ATTRIBUTE, new RequestContext(workspaceId, actorId, traceId, idempotencyKey));
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator/")
        || path.startsWith("/api/v1/auth/")
        || ("GET".equals(request.getMethod()) && "/api/v1/workspaces".equals(path));
  }

  private static UUID parseUuid(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new PdpException(ErrorCode.INVALID_REQUEST, name + "或工作空间上下文缺失");
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new PdpException(ErrorCode.INVALID_REQUEST, name + "格式无效");
    }
  }

  private static UUID resolveActorId(Authentication authentication) {
    if (authentication.getPrincipal() instanceof AuthenticatedActor actor) {
      return actor.userId();
    }
    return parseUuid(authentication.getName(), "操作者标识");
  }
}
