package com.pdp.identity.application;

import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserSession;
import com.pdp.identity.domain.UserStatus;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.identity.port.UserSessionRepository;
import com.pdp.shared.context.RequestContext;
import com.pdp.shared.error.BusinessRuleException;
import com.pdp.shared.error.ErrorCode;
import com.pdp.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 身份生命周期服务。
 *
 * <p>用户启用、停用、离职、会话撤销与刷新凭据撤销。
 * 离职触发即时会话失效；停用保留账户但阻止登录。
 */
@Service
public class IdentityLifecycleService {

    private final UserAccountRepository accountRepository;
    private final UserSessionRepository sessionRepository;

    public IdentityLifecycleService(UserAccountRepository accountRepository,
                                     UserSessionRepository sessionRepository) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
    }

    /** 启用用户：DRAFT/DISABLED → ACTIVE。 */
    public UserAccount activate(UUID userId) {
        UserAccount account = load(userId);
        if (account.status() != UserStatus.DRAFT && account.status() != UserStatus.DISABLED) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "仅 DRAFT 或 DISABLED 状态可启用，当前: " + account.status());
        }
        if (!accountRepository.updateStatus(userId, UserStatus.ACTIVE, account.revision())) {
            throw new BusinessRuleException(ErrorCode.CONFLICT,
                    "启用失败：版本冲突或并发修改");
        }
        return accountRepository.findById(userId).orElseThrow();
    }

    /** 停用用户：ACTIVE → DISABLED，保留账户但阻止登录。 */
    public UserAccount disable(UUID userId, String reason) {
        UserAccount account = load(userId);
        if (account.status() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "仅 ACTIVE 状态可停用，当前: " + account.status());
        }
        if (!accountRepository.updateStatus(userId, UserStatus.DISABLED, account.revision())) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "停用失败：版本冲突");
        }
        // 停用即撤销全部有效会话
        sessionRepository.revokeAllByUser(userId, "DISABLED: " + reason, Instant.now(), 0);
        return accountRepository.findById(userId).orElseThrow();
    }

    /** 离职：→ DEPARTED，即时撤销全部会话与刷新凭据。 */
    public UserAccount depart(UUID userId, String reason) {
        UserAccount account = load(userId);
        if (account.status() == UserStatus.DEPARTED) {
            return account;
        }
        if (!accountRepository.updateStatus(userId, UserStatus.DEPARTED, account.revision())) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "离职失败：版本冲突");
        }
        // 离职即时失效：撤销全部会话
        int revoked = sessionRepository.revokeAllByUser(userId, "DEPARTED: " + reason, Instant.now(), 0);
        return accountRepository.findById(userId).orElseThrow();
    }

    /** 撤销指定用户的全部会话（管理员强制下线）。 */
    public int revokeAllSessions(UUID userId, String reason) {
        load(userId);
        return sessionRepository.revokeAllByUser(userId, "ADMIN: " + reason, Instant.now(), 0);
    }

    /** 撤销单个会话。 */
    public boolean revokeSession(UUID sessionId, String reason) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSession", sessionId));
        return sessionRepository.revoke(sessionId, "MANUAL: " + reason, Instant.now(), session.revision());
    }

    private UserAccount load(UUID userId) {
        return accountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", userId));
    }
}
