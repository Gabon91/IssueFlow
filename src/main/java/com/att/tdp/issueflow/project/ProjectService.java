package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.audit.Audited;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.dto.ProjectCreateRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.ProjectUpdateRequest;
import com.att.tdp.issueflow.project.dto.WorkloadEntry;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the Project aggregate. Soft-delete is implemented by stamping
 * {@code deletedAt}; the {@code @SQLRestriction} on the entity hides deleted rows from
 * default reads. ADMIN-only restore clears that timestamp.
 */
@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          UserRepository userRepository,
                          ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream().map(projectMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return projectMapper.toResponse(load(id));
    }

    @Audited(action = AuditAction.CREATE, entityType = AuditEntityType.PROJECT)
    public ProjectResponse create(ProjectCreateRequest req) {
        User owner = userRepository.findById(req.ownerId())
            .orElseThrow(() -> ResourceNotFoundException.of("User", req.ownerId()));
        Project project = Project.builder()
            .name(req.name())
            .description(req.description())
            .owner(owner)
            .build();
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Audited(action = AuditAction.UPDATE, entityType = AuditEntityType.PROJECT, idArg = 0)
    public ProjectResponse update(Long id, ProjectUpdateRequest req) {
        Project p = load(id);
        if (req.name() != null) {
            p.setName(req.name());
        }
        if (req.description() != null) {
            p.setDescription(req.description());
        }
        return projectMapper.toResponse(p);
    }

    @Audited(action = AuditAction.DELETE, entityType = AuditEntityType.PROJECT, idArg = 0)
    public void softDelete(Long id) {
        Project p = load(id);
        p.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findDeleted() {
        return projectRepository.findDeleted(org.springframework.data.domain.Pageable.unpaged())
            .stream().map(projectMapper::toResponse).toList();
    }

    @Audited(action = AuditAction.RESTORE, entityType = AuditEntityType.PROJECT, idArg = 0)
    public void restore(Long id) {
        Project p = projectRepository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Project", id));
        p.setDeletedAt(null);
    }

    @Transactional(readOnly = true)
    public List<WorkloadEntry> workload(Long id) {
        load(id);
        return userRepository.findDeveloperWorkloadsByProject(id).stream()
            .map(w -> new WorkloadEntry(w.getUserId(), w.getUsername(), w.getOpenTicketCount()))
            .toList();
    }

    private Project load(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Project", id));
    }
}
