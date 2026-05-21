package com.att.tdp.issueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Locks in the contract of the @username mention extractor used by CommentService. */
class MentionParserTest {

    @Test
    void returnsEmptySetForNullOrBlankContent() {
        assertThat(MentionParser.extract(null)).isEmpty();
        assertThat(MentionParser.extract("")).isEmpty();
    }

    @Test
    void extractsSingleMentionAtStartOfContent() {
        assertThat(MentionParser.extract("@alice please review")).containsExactly("alice");
    }

    @Test
    void extractsMentionAfterWhitespaceOrPunctuation() {
        assertThat(MentionParser.extract("Hey @alice and (@bob_42), look here"))
            .containsExactlyInAnyOrder("alice", "bob_42");
    }

    @Test
    void ignoresAtSignEmbeddedInEmailAddress() {
        assertThat(MentionParser.extract("contact bob@example.com please")).isEmpty();
    }

    @Test
    void deduplicatesMentionsPreservingFirstOccurrenceOrder() {
        Set<String> result = MentionParser.extract("@alice ping @bob, then @alice again");
        assertThat(result).containsExactly("alice", "bob");
    }

    @Test
    void enforcesMinimumLengthOfThreeCharacters() {
        assertThat(MentionParser.extract("hi @ab and @abc")).containsExactly("abc");
    }

    @Test
    void allowsUnderscoresAndDigitsInUsername() {
        assertThat(MentionParser.extract("@user_1 and @u2_3"))
            .containsExactlyInAnyOrder("user_1", "u2_3");
    }

    @Test
    void capsUsernameAtFiftyCharactersByEnforcingPatternBoundary() {
        String fifty = "a".repeat(50);
        assertThat(MentionParser.extract("hello @" + fifty)).containsExactly(fifty);
    }
}
