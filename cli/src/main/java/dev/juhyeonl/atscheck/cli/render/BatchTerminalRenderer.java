package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.cli.batch.BatchCheckResult;
import dev.juhyeonl.atscheck.cli.batch.BatchJobResult;
import dev.juhyeonl.atscheck.core.model.Finding;
import dev.juhyeonl.atscheck.core.model.SkillGap;
import dev.juhyeonl.atscheck.core.model.Status;
import dev.juhyeonl.atscheck.core.model.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class BatchTerminalRenderer {
    private static final String SEPARATOR = "  ";
    private static final String SUMMARY_SEPARATOR = " \u00b7 ";
    private static final int VERDICT_MAX_WIDTH = 7;
    private static final int COMPANY_MAX_WIDTH = 16;
    private static final int TITLE_MAX_WIDTH = 24;
    private static final int REASON_MAX_WIDTH = 22;
    private static final int URL_MIN_WIDTH = 20;

    private BatchTerminalRenderer() {
    }

    public static String render(BatchCheckResult batch, int terminalWidth, boolean hyperlinks) {
        return render(batch, terminalWidth, hyperlinks, TerminalLanguage.EN);
    }

    public static String render(
            BatchCheckResult batch,
            int terminalWidth,
            boolean hyperlinks,
            TerminalLanguage language
    ) {
        Objects.requireNonNull(language, "language");

        List<Row> rows = batch.jobs().stream()
                .map(job -> rowFor(job, hyperlinks, language))
                .toList();
        Layout layout = layoutFor(rows, terminalWidth, language);

        StringBuilder output = new StringBuilder();
        appendRow(output, layout, headerRow(language));
        for (Row row : rows) {
            appendRow(output, layout, row);
        }
        output.append("\n");
        output.append(summaryLine(batch, language)).append("\n");
        return output.toString();
    }

    private static Row rowFor(BatchJobResult job, boolean hyperlinks, TerminalLanguage language) {
        String displayUrl = displayUrl(job.frontMatter().url());
        return new Row(
                LocalizedText.verdict(job.result().verdict(), language),
                job.displayCompany(),
                job.displayTitle(),
                reasonFor(job, language),
                displayUrl,
                hyperlinks && !displayUrl.isBlank() ? hyperlinkTarget(job.frontMatter().url()) : null
        );
    }

    private static String reasonFor(BatchJobResult job, TerminalLanguage language) {
        Verdict verdict = job.result().verdict();
        if (verdict == Verdict.SKIP) {
            return SummaryTranslator.translate(firstSummary(job, Status.FAIL), language);
        }
        if (verdict == Verdict.REVIEW) {
            return SummaryTranslator.translate(firstSummary(job, Status.REVIEW), language);
        }

        SkillGap skillGap = job.result().skillGap();
        if (skillGap != null && !skillGap.missingRequired().isEmpty()) {
            return (language == TerminalLanguage.KO ? "부족: " : "missing: ")
                    + joinSkills(skillGap.missingRequired());
        }
        return SummaryTranslator.translate("full match", language);
    }

    private static String firstSummary(BatchJobResult job, Status status) {
        return job.result().findings().stream()
                .filter(finding -> finding.status() == status)
                .findFirst()
                .map(Finding::summary)
                .orElse("");
    }

    private static String joinSkills(Set<String> skills) {
        return skills.stream()
                .map(TerminalRenderer::displayNameFor)
                .collect(Collectors.joining(", "));
    }

    private static String displayUrl(String url) {
        String stripped = url.strip();
        return stripped.replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*://", "");
    }

    private static String hyperlinkTarget(String url) {
        String stripped = url.strip();
        return stripped.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*") ? stripped : "https://" + stripped;
    }

    private static String hyperlink(String target, String displayUrl) {
        return "\033]8;;" + target + "\033\\" + displayUrl + "\033]8;;\033\\";
    }

    private static Layout layoutFor(List<Row> rows, int terminalWidth, TerminalLanguage language) {
        List<Column> columns = new ArrayList<>(List.of(
                new Column(header("VERDICT", language), Row::verdict, verdictMaxWidth(language), false),
                new Column(header("COMPANY", language), Row::company, COMPANY_MAX_WIDTH, false),
                new Column(header("TITLE", language), Row::title, TITLE_MAX_WIDTH, false),
                new Column(header("REASON", language), Row::reason, REASON_MAX_WIDTH, false)
        ));
        int[] widths = widthsFor(columns, rows, language);

        if (hasUrl(rows)) {
            expandKoreanReasonBeforeUrl(columns, rows, widths, terminalWidth, language);
            int urlWidth = terminalWidth - totalWidth(widths) - SEPARATOR.length();
            if (urlWidth >= URL_MIN_WIDTH) {
                columns.add(new Column(header("URL", language), Row::url, Integer.MAX_VALUE, true));
                return new Layout(columns, append(widths, urlWidth));
            }
        }

        shrinkToFitWithoutUrl(columns, widths, terminalWidth);
        return new Layout(columns, widths);
    }

    private static void expandKoreanReasonBeforeUrl(
            List<Column> columns,
            List<Row> rows,
            int[] widths,
            int terminalWidth,
            TerminalLanguage language
    ) {
        if (language != TerminalLanguage.KO) {
            return;
        }

        int spareWidth = terminalWidth - totalWidth(widths) - SEPARATOR.length() - URL_MIN_WIDTH;
        if (spareWidth <= 0) {
            return;
        }

        int reasonIndex = 3;
        int naturalReasonWidth = naturalWidthFor(columns.get(reasonIndex), rows, language);
        int growth = Math.min(spareWidth, Math.max(0, naturalReasonWidth - widths[reasonIndex]));
        widths[reasonIndex] += growth;
    }

    private static int verdictMaxWidth(TerminalLanguage language) {
        return language == TerminalLanguage.KO
                ? DisplayWidth.width(LocalizedText.verdict(Verdict.APPLY, language))
                : VERDICT_MAX_WIDTH;
    }

    private static String header(String english, TerminalLanguage language) {
        if (language != TerminalLanguage.KO) {
            return english;
        }

        return switch (english) {
            case "VERDICT" -> "판정";
            case "COMPANY" -> "회사";
            case "TITLE" -> "직무";
            case "REASON" -> "사유";
            case "URL" -> "URL";
            default -> english;
        };
    }

    private static boolean hasUrl(List<Row> rows) {
        return rows.stream().anyMatch(row -> !row.url().isBlank());
    }

    private static int[] widthsFor(List<Column> columns, List<Row> rows, TerminalLanguage language) {
        int[] widths = new int[columns.size()];
        Row header = headerRow(language);
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            Column column = columns.get(columnIndex);
            widths[columnIndex] = column.cappedWidth(header);
            for (Row row : rows) {
                widths[columnIndex] = Math.max(widths[columnIndex], column.cappedWidth(row));
            }
        }
        return widths;
    }

    private static int naturalWidthFor(Column column, List<Row> rows, TerminalLanguage language) {
        int width = DisplayWidth.width(column.value(headerRow(language)));
        for (Row row : rows) {
            width = Math.max(width, DisplayWidth.width(column.value(row)));
        }
        return width;
    }

    private static int[] append(int[] widths, int width) {
        int[] appended = new int[widths.length + 1];
        System.arraycopy(widths, 0, appended, 0, widths.length);
        appended[widths.length] = width;
        return appended;
    }

    private static void shrinkToFitWithoutUrl(List<Column> columns, int[] widths, int terminalWidth) {
        int overflow = totalWidth(widths) - terminalWidth;
        if (overflow <= 0) {
            return;
        }

        overflow = shrink(widths, 3, DisplayWidth.width(columns.get(3).header()), overflow);
        overflow = shrink(widths, 2, DisplayWidth.width(columns.get(2).header()), overflow);
        overflow = shrink(widths, 1, DisplayWidth.width(columns.get(1).header()), overflow);
        overflow = shrink(widths, 0, DisplayWidth.width(columns.get(0).header()), overflow);

        if (overflow > 0) {
            overflow = shrink(widths, 3, 1, overflow);
            overflow = shrink(widths, 2, 1, overflow);
            overflow = shrink(widths, 1, 1, overflow);
            shrink(widths, 0, 1, overflow);
        }
    }

    private static int shrink(int[] widths, int index, int minimumWidth, int overflow) {
        int shrinkBy = Math.min(Math.max(0, widths[index] - minimumWidth), overflow);
        widths[index] -= shrinkBy;
        return overflow - shrinkBy;
    }

    private static int totalWidth(int[] widths) {
        int total = Math.max(0, widths.length - 1) * SEPARATOR.length();
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    private static Row headerRow(TerminalLanguage language) {
        return new Row(
                header("VERDICT", language),
                header("COMPANY", language),
                header("TITLE", language),
                header("REASON", language),
                header("URL", language),
                null
        );
    }

    private static void appendRow(StringBuilder output, Layout layout, Row row) {
        for (int columnIndex = 0; columnIndex < layout.columns().size(); columnIndex++) {
            Column column = layout.columns().get(columnIndex);
            String value = column.value(row);
            int width = layout.widths()[columnIndex];
            boolean last = columnIndex == layout.columns().size() - 1;
            String truncated = DisplayWidth.truncate(value, width);
            String rendered = column.url() && row.urlTarget() != null
                    ? hyperlink(row.urlTarget(), truncated)
                    : truncated;
            output.append(rendered).append(" ".repeat(Math.max(0, width - DisplayWidth.width(truncated))));
            if (!last) {
                output.append(SEPARATOR);
            }
        }
        output.append("\n");
    }

    private static String summaryLine(BatchCheckResult batch, TerminalLanguage language) {
        if (language == TerminalLanguage.KO) {
            return koreanSummaryLine(batch);
        }

        List<String> parts = new ArrayList<>();
        parts.add(batch.total() + " jobs");
        if (batch.applyCount() > 0) {
            parts.add(batch.applyCount() + " apply");
        }
        if (batch.reviewCount() > 0) {
            parts.add(batch.reviewCount() + " review");
        }
        if (batch.skipCount() > 0) {
            parts.add(batch.skipCount() + " skip");
        }
        return String.join(SUMMARY_SEPARATOR, parts);
    }

    private static String koreanSummaryLine(BatchCheckResult batch) {
        List<String> parts = new ArrayList<>();
        parts.add("공고 " + batch.total() + "건");
        if (batch.applyCount() > 0) {
            parts.add("지원 " + batch.applyCount());
        }
        if (batch.reviewCount() > 0) {
            parts.add("확인 " + batch.reviewCount());
        }
        if (batch.skipCount() > 0) {
            parts.add("제외 " + batch.skipCount());
        }
        return String.join(SUMMARY_SEPARATOR, parts);
    }

    private interface CellValue {
        String value(Row row);
    }

    private record Column(String header, CellValue value, int maxWidth, boolean url) {
        private String value(Row row) {
            return value.value(row);
        }

        private int cappedWidth(Row row) {
            return Math.min(maxWidth, DisplayWidth.width(value(row)));
        }
    }

    private record Row(
            String verdict,
            String company,
            String title,
            String reason,
            String url,
            String urlTarget
    ) {
    }

    private record Layout(List<Column> columns, int[] widths) {
    }
}
