package dev.juhyeonl.atscheck.cli.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PdfTextExtractorTest {
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @TempDir
    private Path tempDir;

    @Test
    void extractsTextFromSinglePagePdf() throws IOException {
        Path pdf = tempDir.resolve("simple.pdf");
        writePdf(pdf, new String[][] {{"Sample Resume", "Java developer with PDF experience."}});

        String text = new PdfTextExtractor().extract(pdf);

        assertThat(text).contains("Sample Resume", "Java developer with PDF experience.");
    }

    @Test
    void extractsTextFromMultiPagePdf() throws IOException {
        Path pdf = tempDir.resolve("multi-page.pdf");
        writePdf(
                pdf,
                new String[][] {
                    {"Profile", "Builds maintainable JVM services.", "Enjoys concise documentation."},
                    {"Experience", "Led migration planning.", "Improved test coverage."}
                });

        String text = new PdfTextExtractor().extract(pdf);

        assertThat(text)
                .contains("Profile", "Builds maintainable JVM services.", "Experience", "Improved test coverage.");
    }

    private static void writePdf(Path pdf, String[][] pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String[] lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.setFont(FONT, 12);
                    content.beginText();
                    content.newLineAtOffset(72, 720);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLineAtOffset(0, -18);
                    }
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }
    }
}
