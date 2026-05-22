package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.mention.dto.MentionPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the per-user mentions inbox. Callers can only read their own inbox;
 * ADMINs are exempt so support staff can review on a user's behalf.
 */
@RestController
@RequestMapping("/users/{userId}/mentions")
@Validated
public class MentionController {

    private final MentionService mentionService;

    public MentionController(MentionService mentionService) {
        this.mentionService = mentionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or principal.userId == #userId")
    public MentionPage list(@PathVariable Long userId,
                            @RequestParam(defaultValue = "1") @Min(1) int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return mentionService.inbox(userId, page, pageSize);
    }
}
