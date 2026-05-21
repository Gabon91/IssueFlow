package com.att.tdp.issueflow.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link Project}. JPQL/derived queries inherit the
 * {@code @SQLRestriction("deleted_at IS NULL")} filter declared on the entity, so default
 * reads exclude soft-deleted rows. The {@code *IncludingDeleted} methods escape that filter
 * via native SQL for {@code ADMIN} list/restore flows.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    List<Project> findByOwnerId(Long ownerId);

    boolean existsByNameIgnoreCase(String name);

    /** ADMIN-only: list rows that were soft-deleted (deleted_at IS NOT NULL). */
    @Query(value = "SELECT * FROM projects WHERE deleted_at IS NOT NULL", nativeQuery = true)
    Page<Project> findDeleted(Pageable pageable);

    /** ADMIN-only: load a single project regardless of its soft-delete state. */
    @Query(value = "SELECT * FROM projects WHERE id = :id", nativeQuery = true)
    Optional<Project> findByIdIncludingDeleted(Long id);
}
