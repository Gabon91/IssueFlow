package com.att.tdp.issueflow.ticket.csv;

import com.att.tdp.issueflow.common.error.ResourceNotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.csv.dto.ImportRowError;
import com.att.tdp.issueflow.ticket.csv.dto.ImportSummary;
import com.att.tdp.issueflow.ticket.domain.Ticket;
import com.att.tdp.issueflow.ticket.domain.TicketPriority;
import com.att.tdp.issueflow.ticket.domain.TicketRepository;
import com.att.tdp.issueflow.ticket.domain.TicketStatus;
import com.att.tdp.issueflow.ticket.domain.TicketType;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk CSV export and import for tickets. Export streams rows in pages of {@link #EXPORT_PAGE_SIZE}
 * to avoid loading an entire project's history into memory. Import validates every row in-memory
 * first, then persists only the well-formed ones in a single batch — so a single bad row never
 * rolls back the rest and the returned {@link ImportSummary} is consistent with what's on disk.
 */
@Service
@Transactional
public class TicketCsvService {

    static final String[] CSV_HEADERS =
        {"id", "title", "description", "status", "priority", "type", "assigneeId"};

    /** Required input columns on import; {@code id} is optional and always ignored. */
    static final Set<String> REQUIRED_IMPORT_COLUMNS =
        Set.of("title", "description", "status", "priority", "type", "assigneeId");

    static final int EXPORT_PAGE_SIZE = 500;

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final Counter csvImportTickets;

    public TicketCsvService(TicketRepository ticketRepository,
                            ProjectRepository projectRepository,
                            UserRepository userRepository,
                            MeterRegistry meterRegistry) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.csvImportTickets = Counter.builder("issueflow.csv.import.tickets")
            .description("Tickets created by POST /tickets/import")
            .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public void exportToCsv(Long projectId, Writer writer) throws IOException {
        requireProject(projectId);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            Pageable page = PageRequest.of(0, EXPORT_PAGE_SIZE);
            Page<Ticket> slice;
            do {
                slice = ticketRepository.findByProjectId(projectId, page);
                for (Ticket t : slice.getContent()) {
                    printer.printRecord(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription(),
                        t.getStatus(),
                        t.getPriority(),
                        t.getType(),
                        t.getAssignee() != null ? t.getAssignee().getId() : null);
                }
                printer.flush();
                page = slice.nextPageable();
            } while (slice.hasNext());
        }
    }

    @Observed(name = "issueflow.csv.import", contextualName = "csv.import")
    public ImportSummary importFromCsv(Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        Project project = requireProject(projectId);
        List<Ticket> toSave = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        Map<Long, User> userCache = new HashMap<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader().setSkipHeaderRecord(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            Set<String> headers = parser.getHeaderMap().keySet();
            if (!headers.containsAll(REQUIRED_IMPORT_COLUMNS)) {
                throw new IllegalArgumentException(
                    "CSV header missing required columns. Expected: " + REQUIRED_IMPORT_COLUMNS
                        + ", found: " + headers);
            }
            for (CSVRecord rec : parser) {
                long line = rec.getRecordNumber() + 1; // +1 for the header line
                try {
                    toSave.add(buildTicket(rec, project, userCache));
                } catch (Exception ex) {
                    errors.add(new ImportRowError(line, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read CSV: " + ex.getMessage());
        }
        ticketRepository.saveAll(toSave);
        csvImportTickets.increment(toSave.size());
        return new ImportSummary(toSave.size(), errors.size(), errors);
    }

    private Ticket buildTicket(CSVRecord rec, Project project, Map<Long, User> userCache) {
        String title = trimOrNull(rec.get("title"));
        if (title == null || title.isEmpty()) throw new IllegalArgumentException("title is required");
        if (title.length() > 200) throw new IllegalArgumentException("title exceeds 200 characters");
        String description = trimOrNull(rec.get("description"));
        if (description != null && description.length() > 10_000) {
            throw new IllegalArgumentException("description exceeds 10000 characters");
        }
        TicketStatus status = parseEnum(TicketStatus.class, rec.get("status"), "status");
        TicketPriority priority = parseEnum(TicketPriority.class, rec.get("priority"), "priority");
        TicketType type = parseEnum(TicketType.class, rec.get("type"), "type");
        User assignee = resolveAssignee(trimOrNull(rec.get("assigneeId")), userCache);
        return Ticket.builder()
            .title(title).description(description)
            .status(status).priority(priority).type(type)
            .project(project).assignee(assignee)
            .isOverdue(false).build();
    }

    private User resolveAssignee(String raw, Map<Long, User> cache) {
        if (raw == null || raw.isEmpty()) return null;
        long id;
        try { id = Long.parseLong(raw); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("assigneeId must be numeric"); }
        if (cache.containsKey(id)) return cache.get(id);
        User u = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown assigneeId: " + id));
        cache.put(id, u);
        return u;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        String v = trimOrNull(raw);
        if (v == null) throw new IllegalArgumentException(field + " is required");
        try { return Enum.valueOf(type, v); }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + field + " value \"" + v + "\"");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }
}
