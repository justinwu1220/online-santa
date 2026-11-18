package com.onlinesanta.closing;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/** 收據清單的篩選條件。 */
final class ReceiptSpecifications {

    private ReceiptSpecifications() {
    }

    static Specification<DonationReceipt> search(UUID campaignId, ReceiptStatus status, PdfStatus pdfStatus, String q) {
        return (root, query, cb) -> {
            var predicate = cb.equal(root.get("campaignId"), campaignId);
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (pdfStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("pdfStatus"), pdfStatus));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.strip().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("receiptNo")), like),
                        cb.like(cb.lower(root.get("donorName")), like),
                        cb.like(cb.lower(root.get("organizationName")), like)));
            }
            return predicate;
        };
    }
}
