package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.dependency.dto.DependencyCreateRequest;
import com.att.tdp.issueflow.dependency.dto.DependencyEntry;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for ticket-to-ticket blocker edges. */
@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
public class DependencyController {

    private final DependencyService dependencyService;

    public DependencyController(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @GetMapping
    public List<DependencyEntry> list(@PathVariable Long ticketId) {
        return dependencyService.findBlockers(ticketId);
    }

    @PostMapping
    public void add(@PathVariable Long ticketId,
                    @Valid @RequestBody DependencyCreateRequest body) {
        dependencyService.add(ticketId, body.blockedBy());
    }

    @DeleteMapping("/{blockerId}")
    public void remove(@PathVariable Long ticketId, @PathVariable Long blockerId) {
        dependencyService.remove(ticketId, blockerId);
    }
}
