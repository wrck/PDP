package com.pdp.identity.application;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.port.AuthorizationPolicyPort;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.shared.context.ActorId;
import com.pdp.shared.context.WorkspaceId;
import java.util.Map;
import java.util.UUID;

/**
 * 统一认证后授权决策入口。
 *
 * <p>每次决策重新读取账户状态与授权版本，确保撤销成功后的新请求立即生效。
 */
public final class AuthorizationService implements WorkspaceBoundaryVerifier {
  private final UserAccountRepository accounts;
  private final AuthorizationPolicyPort policies;

  public AuthorizationService(UserAccountRepository accounts, AuthorizationPolicyPort policies) {
    this.accounts = accounts;
    this.policies = policies;
  }

  public AuthorizationDecision decide(AuthorizationRequest request) {
    var account = accounts.findById(request.actorId().value()).orElse(null);
    if (account == null) {
      return AuthorizationDecision.deny("ACTOR_NOT_FOUND", "操作者不存在", -1);
    }
    if (account.status() != UserAccount.Status.ACTIVE) {
      return AuthorizationDecision.deny(
          "ACTOR_DISABLED", "操作者不是活动状态", account.authorizationVersion());
    }
    if (!request.resource().ownerWorkspaceId().equals(request.workspaceId())) {
      return AuthorizationDecision.deny(
          "RESOURCE_WORKSPACE_MISMATCH", "资源不属于请求工作空间", account.authorizationVersion());
    }
    if (!policies.isAllowed(request)) {
      return AuthorizationDecision.deny(
          "POLICY_DENIED", "授权策略拒绝该动作或资源范围", account.authorizationVersion());
    }
    return AuthorizationDecision.allow(account.authorizationVersion());
  }

  @Override
  public void requireWorkspaceAccess(ActorId actorId, WorkspaceId workspaceId) {
    var workspaceBoundary =
        new ResourceDescriptor("workspace", workspaceId.value(), workspaceId, Map.of());
    var decision =
        decide(new AuthorizationRequest(actorId, workspaceId, "workspace.access", workspaceBoundary));
    if (!decision.allowed()) {
      throw new AuthorizationDeniedException(decision);
    }
  }
}
