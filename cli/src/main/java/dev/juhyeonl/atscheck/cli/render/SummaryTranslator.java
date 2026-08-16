package dev.juhyeonl.atscheck.cli.render;

import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SummaryTranslator {
    private static final Map<String, String> FIXED_KO = Map.ofEntries(
            Map.entry("English only", "영어만 요구"),
            Map.entry("Finnish required", "핀란드어 필수"),
            Map.entry("Swedish required", "스웨덴어 필수"),
            Map.entry("Finnish is a plus", "핀란드어 우대"),
            Map.entry("Swedish is a plus", "스웨덴어 우대"),
            Map.entry("Finnish explicitly not required", "핀란드어 불필요 명시"),
            Map.entry("Swedish explicitly not required", "스웨덴어 불필요 명시"),
            Map.entry("Finnish mentioned, no requirement signal", "핀란드어 언급, 요구 신호 없음"),
            Map.entry("Swedish mentioned, no requirement signal", "스웨덴어 언급, 요구 신호 없음"),
            Map.entry("Finnish - ambiguous requirement", "핀란드어 - 요구 여부 불명확"),
            Map.entry("Swedish - ambiguous requirement", "스웨덴어 - 요구 여부 불명확"),
            Map.entry("Finnish required (you have it)", "핀란드어 필수 (보유함)"),
            Map.entry("Swedish required (you have it)", "스웨덴어 필수 (보유함)"),
            Map.entry("not specified", "명시 없음"),
            Map.entry("not required", "요구 없음"),
            Map.entry("no seniority marker", "레벨 표시 없음"),
            Map.entry("full match", "모두 충족")
    );
    private static final Pattern EXPERIENCE = Pattern.compile("^(.+) \\(profile: (\\d+), tolerance: (\\d+)\\)$");
    private static final Pattern YEAR_RANGE = Pattern.compile("^(\\d{1,2})\\s*(?:-|to)\\s*(\\d{1,2})\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_OR_MORE = Pattern.compile("^(\\d{1,2})\\s+or\\s+more\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PLUS = Pattern.compile("^(\\d{1,2})\\+\\s*years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_MINIMUM = Pattern.compile("^(?:at\\s+least|minimum(?:\\s+of)?)\\s+(\\d{1,2})\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_OVER = Pattern.compile("^(?:over|more\\s+than)\\s+(\\d{1,2})\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_EXACT = Pattern.compile("^(\\d{1,2})\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_YEAR = Pattern.compile("^(one|two|three|four|five|six|seven|eight|nine|ten)\\s+years?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_NO_MARKER = Pattern.compile("^(.+) \\(no seniority marker\\)$");
    private static final Pattern TITLE_PROFILE_MAX = Pattern.compile("^(.+) \\(profile max: ([^)]+)\\)$");
    private static final Pattern DEGREE = Pattern.compile("^(Bachelor|Master|PhD) (required|or equivalent experience) \\(profile: ([A-Z]+)\\)$");
    private static final Pattern MISSING_SKILLS = Pattern.compile("^missing (\\d+) required, (\\d+) nice$");
    private static final Map<String, String> WORD_NUMBERS = Map.ofEntries(
            Map.entry("one", "1"),
            Map.entry("two", "2"),
            Map.entry("three", "3"),
            Map.entry("four", "4"),
            Map.entry("five", "5"),
            Map.entry("six", "6"),
            Map.entry("seven", "7"),
            Map.entry("eight", "8"),
            Map.entry("nine", "9"),
            Map.entry("ten", "10")
    );

    private SummaryTranslator() {
    }

    static String translate(String summary, TerminalLanguage language) {
        if (language != TerminalLanguage.KO) {
            return summary;
        }

        String fixed = FIXED_KO.get(summary);
        if (fixed != null) {
            return fixed;
        }

        String translated = translateExperience(summary);
        if (translated != null) {
            return translated;
        }

        translated = translateSeniority(summary);
        if (translated != null) {
            return translated;
        }

        translated = translateDegree(summary);
        if (translated != null) {
            return translated;
        }

        translated = translateMissingSkills(summary);
        if (translated != null) {
            return translated;
        }

        return summary;
    }

    private static String translateExperience(String summary) {
        Matcher matcher = EXPERIENCE.matcher(summary);
        if (!matcher.matches()) {
            return null;
        }

        String years = translateYearsPhrase(matcher.group(1));
        if (years == null) {
            return summary;
        }
        return years + " (내 경력: " + matcher.group(2) + ", 허용: " + matcher.group(3) + ")";
    }

    private static String translateYearsPhrase(String phrase) {
        Matcher matcher = YEAR_RANGE.matcher(phrase);
        if (matcher.matches()) {
            return matcher.group(1) + "-" + matcher.group(2) + "년";
        }

        matcher = YEAR_OR_MORE.matcher(phrase);
        if (matcher.matches()) {
            return matcher.group(1) + "년 이상";
        }

        matcher = YEAR_PLUS.matcher(phrase);
        if (matcher.matches()) {
            return matcher.group(1) + "년 이상";
        }

        matcher = YEAR_MINIMUM.matcher(phrase);
        if (matcher.matches()) {
            return "최소 " + matcher.group(1) + "년";
        }

        matcher = YEAR_OVER.matcher(phrase);
        if (matcher.matches()) {
            return matcher.group(1) + "년 초과";
        }

        matcher = YEAR_EXACT.matcher(phrase);
        if (matcher.matches()) {
            return matcher.group(1) + "년";
        }

        matcher = WORD_YEAR.matcher(phrase);
        if (matcher.matches()) {
            return WORD_NUMBERS.get(matcher.group(1).toLowerCase(Locale.ROOT)) + "년";
        }

        return null;
    }

    private static String translateSeniority(String summary) {
        Matcher matcher = TITLE_NO_MARKER.matcher(summary);
        if (matcher.matches()) {
            return matcher.group(1) + " (레벨 표시 없음)";
        }

        matcher = TITLE_PROFILE_MAX.matcher(summary);
        if (matcher.matches()) {
            return matcher.group(1) + " (내 상한: " + matcher.group(2) + ")";
        }

        return null;
    }

    private static String translateDegree(String summary) {
        Matcher matcher = DEGREE.matcher(summary);
        if (!matcher.matches()) {
            return null;
        }

        String degree = translateDegreeLabel(matcher.group(1));
        String requirement = switch (matcher.group(2)) {
            case "required" -> "필수";
            case "or equivalent experience" -> "또는 동등 경력";
            default -> null;
        };
        if (degree == null || requirement == null) {
            return summary;
        }
        return degree + " " + requirement + " (내 학위: " + translateDegreeValue(matcher.group(3)) + ")";
    }

    private static String translateDegreeLabel(String value) {
        return switch (value) {
            case "Bachelor" -> "학사";
            case "Master" -> "석사";
            case "PhD" -> "박사";
            default -> null;
        };
    }

    private static String translateDegreeValue(String value) {
        return switch (value) {
            case "NONE" -> "없음";
            case "BACHELOR" -> "학사";
            case "MASTER" -> "석사";
            case "PHD" -> "박사";
            default -> value;
        };
    }

    private static String translateMissingSkills(String summary) {
        Matcher matcher = MISSING_SKILLS.matcher(summary);
        if (!matcher.matches()) {
            return null;
        }
        return "필수 " + matcher.group(1) + "개, 우대 " + matcher.group(2) + "개 부족";
    }
}
