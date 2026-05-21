package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentCreateRequest;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CommentUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for comments nested under a ticket. */
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable Long ticketId) {
        return commentService.findByTicket(ticketId);
    }

    @PostMapping
    public CommentResponse create(@PathVariable Long ticketId,
                                  @Valid @RequestBody CommentCreateRequest body) {
        return commentService.create(ticketId, body);
    }

    @PatchMapping("/{commentId}")
    public CommentResponse update(@PathVariable Long ticketId,
                                  @PathVariable Long commentId,
                                  @Valid @RequestBody CommentUpdateRequest body) {
        return commentService.update(ticketId, commentId, body);
    }

    @DeleteMapping("/{commentId}")
    public void delete(@PathVariable Long ticketId, @PathVariable Long commentId) {
        commentService.delete(ticketId, commentId);
    }
}
