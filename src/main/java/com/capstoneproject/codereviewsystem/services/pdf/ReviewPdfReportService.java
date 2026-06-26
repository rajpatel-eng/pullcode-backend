package com.capstoneproject.codereviewsystem.services.pdf;

import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.CommitReviewResponse;
import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.ErrorItem;
import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.FileReviewItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class ReviewPdfReportService {

    private static final float MARGIN       = 50f;
    private static final float PAGE_WIDTH   = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT  = PDRectangle.A4.getHeight();
    private static final float CONTENT_W    = PAGE_WIDTH - 2 * MARGIN;
    private static final float LINE_H       = 14f;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public String generateBase64Pdf(CommitReviewResponse review, String userName) {
        try (PDDocument doc = new PDDocument()) {
            PDFont bold  = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont plain = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont mono  = new PDType1Font(Standard14Fonts.FontName.COURIER);

            PageWriter pw = new PageWriter(doc, bold, plain, mono);

            pw.writeLine("Code Review Report", bold, 18, true);
            pw.writeLine("Prepared for: " + userName, plain, 11, false);
            pw.writeLine("Commit: " + review.getCommitId(), plain, 11, false);
            pw.writeLine("Source: " + review.getSource(), plain, 11, false);
            if (review.getReviewedAt() != null) {
                pw.writeLine("Reviewed at: " + review.getReviewedAt().format(DT_FMT), plain, 11, false);
            }
            pw.blank();

            pw.writeLine("Summary", bold, 14, false);
            pw.writeLine("Total files:       " + review.getTotalFiles(), plain, 11, false);
            pw.writeLine("Sent to AI:        " + review.getFilesSentToAi(), plain, 11, false);
            pw.writeLine("Carried forward:   " + review.getFilesCarriedForward(), plain, 11, false);
            pw.writeLine("Never reviewed:    " + review.getFilesNeverReviewed(), plain, 11, false);
            pw.writeLine("Total issues:      " + review.getTotalErrors(), plain, 11, false);
            if (review.getErrorsBySeverity() != null && !review.getErrorsBySeverity().isEmpty()) {
                pw.writeLine("Issues by severity:", plain, 11, false);
                for (Map.Entry<String, Long> e : review.getErrorsBySeverity().entrySet()) {
                    pw.writeLine("  " + e.getKey() + ": " + e.getValue(), plain, 10, false);
                }
            }
            pw.blank();

            if (review.getFiles() != null) {
                for (FileReviewItem file : review.getFiles()) {
                    pw.writeLine(file.getFilePath(), bold, 11, false);
                    String status = file.isSentToAi()    ? "Reviewed by AI"
                                  : file.isNeverReviewed()? "Never reviewed"
                                  : "Carried forward from " + file.getSourceCommitId();
                    pw.writeLine("  Status: " + status, plain, 10, false);
                    pw.writeLine("  Issues: " + file.getErrorCount(), plain, 10, false);

                    if (file.getErrors() != null && !file.getErrors().isEmpty()) {
                        for (ErrorItem err : file.getErrors()) {
                            String fresh = err.isFresh() ? "[NEW] " : "[CARRIED] ";
                            String loc   = err.getLineNumber() > 0 ? "L" + err.getLineNumber() : "";
                            if (err.getColumnNumber() > 0) loc += ":C" + err.getColumnNumber();
                            String line = String.format("    %s[%s] %s %s",
                                    fresh,
                                    err.getSeverity() != null ? err.getSeverity().name() : "?",
                                    loc,
                                    err.getMessage() != null ? err.getMessage() : "");
                            pw.writeWrapped(line, mono, 9);
                            if (err.getSuggestion() != null && !err.getSuggestion().isBlank()) {
                                pw.writeWrapped("      Suggestion: " + err.getSuggestion(), plain, 9);
                            }
                        }
                    }
                    pw.blank();
                }
            }

            pw.writeLine("— Code Review System", plain, 9, false);

            pw.close();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            log.info("PDF report generated: commitId={} pages={} bytes={}",
                    review.getCommitId(), doc.getNumberOfPages(), bos.size());
            return b64;

        } catch (IOException e) {
            log.error("PDF generation failed for commitId={}: {}", review.getCommitId(), e.getMessage());
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private static class PageWriter {
        private final PDDocument doc;
        private final PDFont bold, plain, mono;

        private PDPage       page;
        private PDPageContentStream cs;
        private float        y;

        PageWriter(PDDocument doc, PDFont bold, PDFont plain, PDFont mono) throws IOException {
            this.doc   = doc;
            this.bold  = bold;
            this.plain = plain;
            this.mono  = mono;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y  = PAGE_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) newPage();
        }

        void blank() throws IOException {
            y -= LINE_H;
            if (y < MARGIN) newPage();
        }

        void writeLine(String text, PDFont font, float size, boolean center) throws IOException {
            ensureSpace(size + 4);
            cs.beginText();
            cs.setFont(font, size);
            float x = center
                    ? (PAGE_WIDTH - font.getStringWidth(safe(text)) / 1000f * size) / 2
                    : MARGIN;
            cs.newLineAtOffset(x, y);
            cs.showText(safe(text));
            cs.endText();
            y -= (size + 4);
        }

        void writeWrapped(String text, PDFont font, float size) throws IOException {
            int maxChars = (int)(CONTENT_W / (size * 0.6));
            List<String> lines = wrap(text, maxChars);
            for (String l : lines) writeLine(l, font, size, false);
        }

        void close() throws IOException {
            if (cs != null) cs.close();
        }

        private static String safe(String s) {
            if (s == null) return "";
            // PDFBox Standard14 only supports Latin-1
            return s.chars()
                    .filter(c -> c < 256)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        }

        private static List<String> wrap(String text, int max) {
            if (text.length() <= max) return List.of(text);
            java.util.List<String> result = new java.util.ArrayList<>();
            while (text.length() > max) {
                int cut = text.lastIndexOf(' ', max);
                if (cut <= 0) cut = max;
                result.add(text.substring(0, cut));
                text = text.substring(cut).stripLeading();
            }
            if (!text.isEmpty()) result.add(text);
            return result;
        }
    }
}