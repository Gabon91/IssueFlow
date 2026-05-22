package com.att.tdp.issueflow.common.audit;

import com.att.tdp.issueflow.auditlog.AuditActor;
import com.att.tdp.issueflow.auditlog.AuditLog;
import com.att.tdp.issueflow.auditlog.AuditLogRepository;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Writes an {@link AuditLog} row after every successful invocation of an {@link Audited} method.
 * Runs in the caller's transaction, so a rollback of the business method also rolls back the
 * audit entry — i.e. only durable mutations are logged. Failures inside the aspect itself are
 * swallowed (with a warn log) so audit issues can never break the API.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void onAuditedMethod(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            Long entityId = resolveEntityId(joinPoint, result, audited.idArg());
            if (entityId == null) {
                log.warn("@Audited on {} produced no entityId; skipping audit row",
                    joinPoint.getSignature().toShortString());
                return;
            }
            Long performedBy = currentUserId();
            AuditActor actor = performedBy != null ? AuditActor.USER : AuditActor.SYSTEM;
            String payload = "{\"method\":\"" + joinPoint.getSignature().toShortString() + "\"}";
            auditLogRepository.save(AuditLog.builder()
                .action(audited.action())
                .entityType(audited.entityType())
                .entityId(entityId)
                .performedBy(performedBy)
                .actor(actor)
                .payload(payload)
                .build());
        } catch (Exception ex) {
            log.warn("Failed to write audit log for {}: {}",
                joinPoint.getSignature().toShortString(), ex.toString());
        }
    }

    private static Long resolveEntityId(JoinPoint jp, Object result, int idArg) {
        Object[] args = jp.getArgs();
        if (idArg >= 0 && idArg < args.length) {
            Object a = args[idArg];
            if (a instanceof Long l) return l;
            if (a instanceof Number n) return n.longValue();
        }
        if (result != null) {
            try {
                Method m = result.getClass().getMethod("id");
                Object v = m.invoke(result);
                if (v instanceof Long l) return l;
                if (v instanceof Number n) return n.longValue();
            } catch (ReflectiveOperationException ignored) {
                // Return value doesn't expose id(); fall through.
            }
        }
        // Last resort: look at the underlying method's declared parameter types for the
        // first Long. Avoids forcing idArg=0 on the typical (Long id, ...) signatures.
        Class<?>[] paramTypes = ((MethodSignature) jp.getSignature()).getMethod().getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == Long.class && args[i] instanceof Long l) return l;
        }
        return null;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof IssueFlowUserDetails iud) return iud.getUserId();
        return null;
    }
}
