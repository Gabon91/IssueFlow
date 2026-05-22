package com.att.tdp.issueflow.ticket.csv;

import com.att.tdp.issueflow.ticket.csv.dto.ImportSummary;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** REST endpoints for CSV bulk export and import of tickets. */
@RestController
@RequestMapping("/tickets")
public class TicketCsvController {

    private final TicketCsvService csvService;

    public TicketCsvController(TicketCsvService csvService) {
        this.csvService = csvService;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam Long projectId) {
        StreamingResponseBody body = out -> {
            try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                csvService.exportToCsv(projectId, w);
            }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"tickets-project-" + projectId + ".csv\"")
            .body(body);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummary importCsv(@RequestParam("file") MultipartFile file,
                                   @RequestParam("projectId") Long projectId) {
        return csvService.importFromCsv(projectId, file);
    }
}
