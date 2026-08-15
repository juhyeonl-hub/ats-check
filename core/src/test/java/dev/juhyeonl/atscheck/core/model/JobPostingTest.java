package dev.juhyeonl.atscheck.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobPostingTest {
    @Test
    @DisplayName("fromText는 첫 번째 비어 있지 않은 줄을 title로 쓰고 전체 텍스트를 body로 보존한다")
    void fromTextUsesFirstNonBlankLineAsTitleAndPreservesBody() {
        String text = "\n  Senior Backend Engineer  \nRequirements:\nJava";

        JobPosting posting = JobPosting.fromText(text);

        assertThat(posting.title()).isEqualTo("Senior Backend Engineer");
        assertThat(posting.body()).isEqualTo(text);
    }

    @Test
    @DisplayName("fromText는 비어 있는 입력에서 빈 title과 원문 body를 만든다")
    void fromTextUsesEmptyTitleForBlankText() {
        JobPosting posting = JobPosting.fromText(" \n\t");

        assertThat(posting.title()).isEmpty();
        assertThat(posting.body()).isEqualTo(" \n\t");
    }
}
