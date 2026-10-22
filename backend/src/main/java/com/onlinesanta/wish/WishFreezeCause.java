package com.onlinesanta.wish;

/**
 * 願望被凍結的原因。同一個願望可以同時有多個原因（例如機構被停權時，某願望本來就因內容疑慮被凍結），
 * 每個原因各自解除，全部解除後願望才真的解除凍結。
 *
 * <p>宣告順序就是優先度（越前面越優先）：畫面只能顯示一個說法時，顯示優先度最高的原因。
 */
public enum WishFreezeCause {
    /** 認領者違規被停權：連動凍結他進行中的認領。 */
    DONOR_SUSPENDED(true),
    /** 機構被停權：連動凍結該機構所有進行中的願望。 */
    ORG_SUSPENDED(true),
    /** 管理員發現內容有疑慮（例如個資外洩），允許機構修改後申請解除。 */
    ADMIN_CONTENT(false),
    /** 管理員的單純凍結，機構不能改內容。 */
    ADMIN_OTHER(false);

    private final boolean system;

    WishFreezeCause(boolean system) {
        this.system = system;
    }

    /** 由停權連動產生、也由停權解除連動解除的原因（不是管理員在願望上直接操作的）。 */
    public boolean isSystem() {
        return system;
    }

    /** 管理員在願望抽屜按「解除凍結」時能解除的原因。 */
    public boolean isLiftableByWishUnfreeze() {
        return !system;
    }
}
