package mz.co.southbeach.tickets.api;

import mz.co.southbeach.tickets.api.dto.TicketPassResponse;
import mz.co.southbeach.tickets.service.QrService;
import mz.co.southbeach.tickets.service.TicketPassService;
import mz.co.southbeach.tickets.service.TicketPdfService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/tickets")
public class PublicTicketController {
    private final TicketPassService passes;
    private final QrService qr;
    private final TicketPdfService pdfs;

    public PublicTicketController(TicketPassService passes, QrService qr, TicketPdfService pdfs) {
        this.passes = passes;
        this.qr = qr;
        this.pdfs = pdfs;
    }

    /** The customer's ticket page data, found by the private token in their link. */
    @GetMapping("/{accessToken}")
    public ResponseEntity<TicketPassResponse> pass(@PathVariable String accessToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(passes.byAccessToken(accessToken));
    }

    /** QR image for one ticket code; only codes that exist are rendered. */
    @GetMapping(value = "/qr/{code}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable String code) {
        passes.requireCode(code);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate()).body(qr.png(code));
    }

    /** The same tickets as a PDF (one page per person), found by the private token. Orders without tickets answer 404. */
    @GetMapping(value = "/{accessToken}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String accessToken) {
        var pdf = pdfs.forAccessToken(accessToken).orElseThrow(() -> new mz.co.southbeach.tickets.service.TicketNotFoundException("Tickets"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"" + pdf.filename() + "\"").body(pdf.bytes());
    }
}
