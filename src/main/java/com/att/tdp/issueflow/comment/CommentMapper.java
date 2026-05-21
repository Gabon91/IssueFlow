package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.MentionedUser;
import com.att.tdp.issueflow.user.User;
import org.mapstruct.Mapper;

/** MapStruct mapper for projecting {@link User} into the comment-response shape. */
@Mapper(componentModel = "spring")
public interface CommentMapper {

    MentionedUser toMentionedUser(User user);
}
