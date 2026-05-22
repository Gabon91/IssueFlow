package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.MentionedUser;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.mention.dto.MentionPage;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the per-user mentions inbox. Pages {@link CommentMention} rows newest
 * first and projects each into the public {@link CommentResponse} shape so the response is
 * a drop-in slice of the comment API the client already consumes.
 */
@Service
@Transactional(readOnly = true)
public class MentionService {

    private final UserRepository userRepository;
    private final CommentMentionRepository mentionRepository;
    private final CommentMapper commentMapper;

    public MentionService(UserRepository userRepository,
                          CommentMentionRepository mentionRepository,
                          CommentMapper commentMapper) {
        this.userRepository = userRepository;
        this.mentionRepository = mentionRepository;
        this.commentMapper = commentMapper;
    }

    public MentionPage inbox(Long userId, int page, int pageSize) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        Page<CommentMention> slice =
            mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(userId, pageable);
        List<CommentResponse> data = slice.getContent().stream()
            .map(m -> toResponse(m.getComment()))
            .toList();
        return new MentionPage(data, slice.getTotalElements(), page);
    }

    private CommentResponse toResponse(Comment c) {
        List<MentionedUser> mentions = mentionRepository.findByCommentId(c.getId()).stream()
            .map(m -> commentMapper.toMentionedUser(m.getMentionedUser()))
            .toList();
        return new CommentResponse(c.getId(), c.getTicket().getId(), c.getAuthor().getId(),
            c.getContent(), mentions, c.getCreatedAt(), c.getUpdatedAt());
    }
}
