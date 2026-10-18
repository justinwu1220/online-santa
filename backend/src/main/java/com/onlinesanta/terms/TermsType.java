package com.onlinesanta.terms;

/** 需要使用者同意的須知種類。目前只有認領者；合作機構須知上線時再加。 */
public enum TermsType {
    DONOR("認領者參與須知");

    private final String title;

    TermsType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
