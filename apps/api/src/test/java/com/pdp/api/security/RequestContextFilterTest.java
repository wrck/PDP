package com.pdp.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.identity.application.WorkspaceBoundaryVerifier;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.error.PdpException;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestContextFilterTest {

  @Test
  void 应从认证与请求头构建上下文并校验工作空间边界() throws Exception {
    UUID actorId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    var verified = new AtomicReference<UUID>();
    WorkspaceBoundaryVerifier verifier =
        (actor, workspace) -> {
          assertThat(actor.value()).isEqualTo(actorId);
          verified.set(workspace.value());
        };
    var filter = new RequestContextFilter(verifier);
    var request = new MockHttpServletRequest("POST", "/api/v1/projects");
    request.addHeader("X-Workspace-Id", workspaceId.toString());
    request.addHeader("X-Trace-Id", "trace-001");
    request.addHeader("Idempotency-Key", "create-project-001");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(actorId.toString(), "n/a", java.util.List.of()));
    var observed = new AtomicReference<RequestContext>();
    FilterChain chain =
        (servletRequest, servletResponse) ->
            observed.set((RequestContext) servletRequest.getAttribute(RequestContextFilter.ATTRIBUTE));

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(verified.get()).isEqualTo(workspaceId);
    assertThat(observed.get().workspaceId().value()).isEqualTo(workspaceId);
    assertThat(observed.get().idempotencyKey()).isPresent();
    SecurityContextHolder.clearContext();
  }

  @Test
  void 工作空间缺失或格式无效必须在进入业务前拒绝() {
    var filter = new RequestContextFilter((actor, workspace) -> {});
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "n/a", java.util.List.of()));
    var request = new MockHttpServletRequest("GET", "/api/v1/projects");

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {}))
        .isInstanceOf(PdpException.class)
        .hasMessageContaining("工作空间");
    SecurityContextHolder.clearContext();
  }
}
