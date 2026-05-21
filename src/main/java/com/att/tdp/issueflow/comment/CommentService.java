package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentCreateRequest;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CommentUpdateRequest;
import com.att.tdp.issueflow.comment.dto.MentionedUser;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.mention.CommentMention;
import com.att.tdp.issueflow.mention.CommentMentionRepository;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the Comment aggregate. Creating or updating a comment runs the
 * {@link MentionParser} over its content, resolves the resulting usernames against
 * {@link UserRepository}, and persists a {@link CommentMention} row per (comment, user) pair
 * in the same transaction. Existing mentions are replaced on update.
 */
@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMentionRepository mentionRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    public CommentService(CommentRepository commentRepository,
                          CommentMentionRepository mentionRepository,
                          TicketRepository ticketRepository,
                          UserRepository userRepository,
                          CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.commentMapper = commentMapper;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> findByTicket(Long ticketId) {
        requireTicket(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId, Pageable.unpaged())
            .map(this::toResponse).toList();
    }

    public CommentResponse create(Long ticketId, CommentCreateRequest req) {
        Ticket ticket = requireTicket(ticketId);
        User author = userRepository.findById(req.authorId())
            .orElseThrow(() -> ResourceNotFoundException.of("User", req.authorId()));
        Comment comment = Comment.builder()
            .ticket(ticket)
            .author(author)
            .content(req.content())
            .build();
        Comment persisted = commentRepository.save(comment);
        List<User> mentioned = resolveMentions(req.content());
        for (User u : mentioned) {
            mentionRepository.save(CommentMention.builder()
                .comment(persisted)
                .mentionedUser(u)
                .build());
        }
        return toResponse(persisted, mentioned);
    }

    public CommentResponse update(Long ticketId, Long commentId, CommentUpdateRequest req) {
        Comment comment = loadCommentOnTicket(ticketId, commentId);
        comment.setContent(req.content());
        // Replace mentions: delete old, re-extract new.
        mentionRepository.deleteByCommentId(commentId);
        mentionRepository.flush();
        List<User> mentioned = resolveMentions(req.content());
        for (User u : mentioned) {
            mentionRepository.save(CommentMention.builder()
                .comment(comment)
                .mentionedUser(u)
                .build());
        }
        return toResponse(comment, mentioned);
    }

    public void delete(Long ticketId, Long commentId) {
        Comment comment = loadCommentOnTicket(ticketId, commentId);
        commentRepository.delete(comment);
    }

    private List<User> resolveMentions(String content) {
        Set<String> usernames = MentionParser.extract(content);
        if (usernames.isEmpty()) return List.of();
        List<User> resolved = new ArrayList<>(usernames.size());
        for (String username : usernames) {
            userRepository.findByUsername(username).ifPresent(resolved::add);
        }
        return resolved;
    }

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> ResourceNotFoundException.of("Ticket", ticketId));
    }

    private Comment loadCommentOnTicket(Long ticketId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Comment", commentId));
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw ResourceNotFoundException.of("Comment", commentId);
        }
        return comment;
    }

    private CommentResponse toResponse(Comment c) {
        List<MentionedUser> mentions = mentionRepository.findByCommentId(c.getId()).stream()
            .map(m -> commentMapper.toMentionedUser(m.getMentionedUser()))
            .toList();
        return new CommentResponse(c.getId(), c.getTicket().getId(), c.getAuthor().getId(),
            c.getContent(), mentions, c.getCreatedAt(), c.getUpdatedAt());
    }

    private CommentResponse toResponse(Comment c, List<User> mentioned) {
        List<MentionedUser> mentions = mentioned.stream().map(commentMapper::toMentionedUser).toList();
        return new CommentResponse(c.getId(), c.getTicket().getId(), c.getAuthor().getId(),
            c.getContent(), mentions, c.getCreatedAt(), c.getUpdatedAt());
    }
}
