package co.tkm.ticketmanagementsystem.service;

import co.tkm.ticketmanagementsystem.model.Ticket;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class PdfTicketService {

    private final QrCodeService qrCodeService;

    public byte[] generatePdf(Ticket ticket) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(400, 600));
            doc.addPage(page);

            byte[] qrBytes = qrCodeService.generateQrPng(ticket.getTicketCode(), 150);
            PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qrBytes, "qr");

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Dark header background
                cs.setNonStrokingColor(0.059f, 0.090f, 0.165f); // #0f172a
                cs.addRect(0, 500, 400, 100);
                cs.fill();

                // Header text
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(24, 555);
                cs.showText(truncate(ticket.getBooking().getShowtime().getMovie().getTitle(), 30));
                cs.endText();

                cs.beginText();
                cs.setFont(regular, 9);
                cs.newLineAtOffset(24, 540);
                cs.showText("E-TICKET");
                cs.endText();

                // Body
                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                float y = 480;
                float labelX = 24;
                float valueX = 130;

                y = drawField(cs, bold, regular, "Date", ticket.getBooking().getShowtime().getShowDate().toString(), labelX, valueX, y);
                y = drawField(cs, bold, regular, "Time", ticket.getBooking().getShowtime().getShowTime().toString(), labelX, valueX, y);
                y = drawField(cs, bold, regular, "Hall", ticket.getBooking().getShowtime().getHall().getName(), labelX, valueX, y);
                y = drawField(cs, bold, regular, "Seat", ticket.getSeat().getSeatCode() + " (" + ticket.getSeat().getSeatType() + ")", labelX, valueX, y);

                String customer = ticket.getBooking().getUser() != null
                        ? ticket.getBooking().getUser().getName()
                        : (ticket.getBooking().getGuestName() != null ? ticket.getBooking().getGuestName() : "Guest");
                y = drawField(cs, bold, regular, "Customer", customer, labelX, valueX, y);
                y = drawField(cs, bold, regular, "Booking Ref", "#" + ticket.getBooking().getId(), labelX, valueX, y);

                // Dashed divider
                y -= 10;
                cs.setStrokingColor(0.7f, 0.7f, 0.7f);
                cs.setLineDashPattern(new float[]{4, 4}, 0);
                cs.moveTo(24, y);
                cs.lineTo(376, y);
                cs.stroke();
                y -= 20;

                // Ticket code
                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                cs.beginText();
                cs.setFont(regular, 8);
                cs.newLineAtOffset(labelX, y);
                cs.showText("TICKET CODE");
                cs.endText();
                y -= 14;
                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(labelX, y);
                cs.showText(ticket.getTicketCode());
                cs.endText();

                // QR code centered
                float qrSize = 130;
                float qrX = (400 - qrSize) / 2f;
                cs.drawImage(qrImage, qrX, 20, qrSize, qrSize);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF ticket", e);
        }
    }

    private float drawField(PDPageContentStream cs, PDType1Font bold, PDType1Font regular,
                             String label, String value, float labelX, float valueX, float y) throws IOException {
        cs.beginText();
        cs.setFont(bold, 8);
        cs.newLineAtOffset(labelX, y);
        cs.showText(label.toUpperCase());
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(valueX, y);
        cs.showText(truncate(value, 30));
        cs.endText();

        return y - 22;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
