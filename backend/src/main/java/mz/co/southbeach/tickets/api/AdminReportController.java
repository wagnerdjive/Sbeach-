package mz.co.southbeach.tickets.api;

import mz.co.southbeach.tickets.service.ReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {
    private final ReportService reports;

    public AdminReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/events")
    public List<ReportService.SalesReport> events() { return reports.allEvents(); }

    @GetMapping("/events/{id}")
    public ReportService.SalesReport event(@PathVariable Long id) { return reports.forEvent(id); }

    /** Contains customers' contact details: staff only, never cached. */
    @GetMapping("/events/{id}/orders.csv")
    public ResponseEntity<byte[]> ordersCsv(@PathVariable Long id) {
        var body = reports.ordersCsv(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("encomendas-evento-" + id + ".csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
