package com.att.tdp.issueflow.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.mention.dto.MentionPage;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Covers the mention inbox: user existence guard, 1-based paging, mapping and ordering. */
@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock UserRepository userRepository;
    @Mock CommentMentionRepository mentionRepository;
    @Mock CommentMapper commentMapper;
    @InjectMocks MentionService service;

    private CommentMention edge(long id, Comment comment) {
        return CommentMention.builder().id(id).comment(comment).build();
    }

    private Comment comment(long id, long ticketId, long authorId, String content) {
        Project p = Project.builder().id(7L).name("p").build();
        Ticket t = Ticket.builder().id(ticketId).title("t").project(p).build();
        User author = User.builder().id(authorId).username("u" + authorId).build();
        return Comment.builder().id(id).ticket(t).author(author).content(content).build();
    }

    @Test
    void inboxThrowsNotFoundForUnknownUser() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.inbox(99L, 1, 20))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(mentionRepository, never())
            .findByMentionedUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inboxReturnsEmptyPageWhenNoMentions() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Page.empty());

        MentionPage result = service.inbox(1L, 1, 20);

        assertThat(result.data()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void inboxTranslatesOneBasedPageToZeroBasedOffset() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Page.empty());

        service.inbox(1L, 3, 25);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mentionRepository)
            .findByMentionedUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(1L),
                pageCaptor.capture());
        Pageable pageable = pageCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void inboxProjectsEachMentionToCommentResponseInRepoOrder() {
        Comment c1 = comment(10L, 100L, 200L, "first @alice");
        Comment c2 = comment(11L, 101L, 201L, "second @alice");
        // PageImpl clamps total down when offset + pageSize > total; pick a pageable that
        // fits inside the declared total so the assertion below sees the value we passed in.
        Page<CommentMention> slice = new PageImpl<>(
            List.of(edge(1L, c1), edge(2L, c2)),
            PageRequest.of(0, 2), 7L);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(slice);
        when(mentionRepository.findByCommentId(10L)).thenReturn(List.of());
        when(mentionRepository.findByCommentId(11L)).thenReturn(List.of());

        MentionPage result = service.inbox(1L, 1, 20);

        assertThat(result.total()).isEqualTo(7L);
        assertThat(result.data())
            .extracting(r -> r.id(), r -> r.content())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(10L, "first @alice"),
                org.assertj.core.groups.Tuple.tuple(11L, "second @alice"));
    }
}
