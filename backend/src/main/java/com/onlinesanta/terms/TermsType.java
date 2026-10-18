package com.onlinesanta.terms;

/** 需要使用者同意的須知種類：認領者與合作機構各一份。 */
public enum TermsType {
    DONOR("認領者參與須知"),
    ORG("合作機構參與須知");

    private final String title;

    TermsType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
