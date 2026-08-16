package dev.juhyeonl.atscheck.cli.render;

import dev.juhyeonl.atscheck.core.model.RuleId;
import dev.juhyeonl.atscheck.core.model.Verdict;

final class LocalizedText {
    private LocalizedText() {
    }

    static String verdictPrefix(TerminalLanguage language) {
        return language == TerminalLanguage.KO ? "판정: " : "VERDICT: ";
    }

    static String verdict(Verdict verdict, TerminalLanguage language) {
        if (language != TerminalLanguage.KO) {
            return verdict.name();
        }

        return switch (verdict) {
            case APPLY -> "지원 가능";
            case REVIEW -> "확인 필요";
            case SKIP -> "제외";
        };
    }

    static String label(RuleId rule, TerminalLanguage language) {
        if (language != TerminalLanguage.KO) {
            return switch (rule) {
                case LANGUAGE -> "Language";
                case EXPERIENCE_YEARS -> "Seniority";
                case DEGREE -> "Degree";
                case SENIORITY_LEVEL -> "Level";
                case SKILLS -> "";
            };
        }

        return switch (rule) {
            case LANGUAGE -> "언어";
            case EXPERIENCE_YEARS -> "연차";
            case DEGREE -> "학위";
            case SENIORITY_LEVEL -> "레벨";
            case SKILLS -> "";
        };
    }

    static String skillLabel(String label, TerminalLanguage language) {
        if (language != TerminalLanguage.KO) {
            return label;
        }

        return switch (label) {
            case "MISSING (required)" -> "부족 (필수)";
            case "MISSING (nice)" -> "부족 (우대)";
            case "MATCHED" -> "보유";
            default -> label;
        };
    }

    static String hardFilterStopped(TerminalLanguage language) {
        return language == TerminalLanguage.KO
                ? "하드 필터에서 분석을 중단했습니다."
                : "Analysis stopped at hard filter.";
    }

    static String borderline(TerminalLanguage language) {
        return language == TerminalLanguage.KO ? "경계선" : "borderline";
    }
}
