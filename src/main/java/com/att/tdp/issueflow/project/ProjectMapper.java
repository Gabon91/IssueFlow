package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link Project} entities and DTOs. */
@Mapper(componentModel = "spring")
public interface ProjectMapper {

    @Mapping(target = "ownerId", source = "owner.id")
    ProjectResponse toResponse(Project project);
}
