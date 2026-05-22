package com.att.tdp.issueflow.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditActor;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.auditlog.AuditLog;
import com.att.tdp.issueflow.auditlog.AuditLogRepository;
import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.user.Role;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Pins down the AOP audit recorder: id resolution priority (idArg > return.id() > first Long arg),
 * actor attribution (authenticated USER vs SYSTEM), and the "never break business" failure mode.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock AuditLogRepository auditLogRepository;
    @InjectMocks AuditAspect aspect;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    /** Sample DTO whose {@code id()} the aspect reflects on when {@code idArg} is unset. */
    record FakeResponse(Long id) {}

    private static JoinPoint jp(Object[] args, Method method) {
        MethodSignature sig = org.mockito.Mockito.mock(MethodSignature.class);
        lenient().when(sig.getMethod()).thenReturn(method);
        lenient().when(sig.toShortString()).thenReturn(method.getName());
        JoinPoint p = org.mockito.Mockito.mock(JoinPoint.class);
        lenient().when(p.getArgs()).thenReturn(args);
        lenient().when(p.getSignature()).thenReturn((Signature) sig);
        return p;
    }

    private Audited audited(AuditAction action, AuditEntityType entity, int idArg) {
        return new Audited() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Audited.class; }
            @Override public AuditAction action() { return action; }
            @Override public AuditEntityType entityType() { return entity; }
            @Override public int idArg() { return idArg; }
        };
    }

    static void target(Long id, String body) {}
    static FakeResponse targetCreating() { return null; }

    @Test
    void idArgWinsOverReturnId() throws Exception {
        Method m = AuditAspectTest.class.getDeclaredMethod("target", Long.class, String.class);
        JoinPoint p = jp(new Object[]{42L, "ignored"}, m);
        FakeResponse result = new FakeResponse(99L);

        aspect.onAuditedMethod(p, audited(AuditAction.UPDATE, AuditEntityType.TICKET, 0), result);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getEntityId()).isEqualTo(42L);
        assertThat(cap.getValue().getActor()).isEqualTo(AuditActor.SYSTEM);
        assertThat(cap.getValue().getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(cap.getValue().getEntityType()).isEqualTo(AuditEntityType.TICKET);
    }

    @Test
    void returnIdUsedWhenIdArgUnset() throws Exception {
        Method m = AuditAspectTest.class.getDeclaredMethod("targetCreating");
        JoinPoint p = jp(new Object[]{}, m);

        aspect.onAuditedMethod(p, audited(AuditAction.CREATE, AuditEntityType.TICKET, -1),
            new FakeResponse(7L));

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getEntityId()).isEqualTo(7L);
    }

    @Test
    void fallbackToFirstLongArgWhenReturnHasNoId() throws Exception {
        Method m = AuditAspectTest.class.getDeclaredMethod("target", Long.class, String.class);
        JoinPoint p = jp(new Object[]{55L, "x"}, m);

        aspect.onAuditedMethod(p, audited(AuditAction.DELETE, AuditEntityType.DEPENDENCY, -1), null);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getEntityId()).isEqualTo(55L);
    }

    @Test
    void authenticatedPrincipalRecordedAsUserActor() throws Exception {
        IssueFlowUserDetails principal = new IssueFlowUserDetails(8L, "alice", Role.DEVELOPER);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Method m = AuditAspectTest.class.getDeclaredMethod("target", Long.class, String.class);
        JoinPoint p = jp(new Object[]{1L, "x"}, m);

        aspect.onAuditedMethod(p, audited(AuditAction.UPDATE, AuditEntityType.TICKET, 0), null);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(cap.capture());
        assertThat(cap.getValue().getActor()).isEqualTo(AuditActor.USER);
        assertThat(cap.getValue().getPerformedBy()).isEqualTo(8L);
    }

    @Test
    void noEntityIdResolvableSkipsSaveButDoesNotThrow() throws Exception {
        Method m = AuditAspectTest.class.getDeclaredMethod("targetCreating");
        JoinPoint p = jp(new Object[]{}, m);

        aspect.onAuditedMethod(p, audited(AuditAction.UPDATE, AuditEntityType.TICKET, -1), "no-id-here");

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void repositoryFailureIsSwallowed() throws Exception {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));
        Method m = AuditAspectTest.class.getDeclaredMethod("target", Long.class, String.class);
        JoinPoint p = jp(new Object[]{1L, "x"}, m);

        // Must not propagate.
        aspect.onAuditedMethod(p, audited(AuditAction.UPDATE, AuditEntityType.TICKET, 0), null);
    }

    @Test
    void argsArrayIsUntouched() throws Exception {
        Method m = AuditAspectTest.class.getDeclaredMethod("target", Long.class, String.class);
        Object[] args = new Object[]{1L, "x"};
        JoinPoint p = jp(args, m);
        aspect.onAuditedMethod(p, audited(AuditAction.UPDATE, AuditEntityType.TICKET, 0), null);
        assertThat(Arrays.asList(args)).containsExactly(1L, "x");
    }
}
