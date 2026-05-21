package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.domain.BaseAuditableEntity;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A project groups related tickets. Projects support soft deletion via {@link #deletedAt};
 * default queries hide deleted rows through the Hibernate {@code @SQLRestriction} filter.
 */
@Entity
@Table(
    name = "projects",
    indexes = {
        @Index(name = "ix_projects_owner", columnList = "owner_id"),
        @Index(name = "ix_projects_deleted_at", columnList = "deleted_at")
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Project extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_projects_owner"))
    private User owner;

    /** Soft-delete marker; {@code null} for live rows. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
