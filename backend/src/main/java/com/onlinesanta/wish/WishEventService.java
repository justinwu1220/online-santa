package com.onlinesanta.wish;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.wish.dto.WishChanges;
import com.onlinesanta.wish.dto.WishChanges.FieldChange;
import com.onlinesanta.wish.dto.WishHistoryItem;

/**
 * 願望歷程：寫入事件、讀回時間軸、與「上次核准的版本」比較、判斷下架修改後能不能直接恢復上架。
 */
@Service
public class WishEventService {

    /** 會改變願望「在不在牆上」的事件；恢復上架要看最近一個是不是「為修改而下架」。 */
    private static final Set<WishEventType> LIFECYCLE = EnumSet.of(
            WishEventType.SUBMITTED, WishEventType.RESUBMITTED, WishEventType.APPROVED,
            WishEventType.REJECTED, WishEventType.WITHDRAWN, WishEventType.TAKEN_DOWN_FOR_EDIT,
            WishEventType.RESTORED);

    private final WishEventRepository events;
    private final AttachmentService attachments;
    private final ObjectMapper json;

    public WishEventService(WishEventRepository events, AttachmentService attachments, ObjectMapper json) {
        this.events = events;
        this.attachments = attachments;
        this.json = json;
    }

    /** 寫一筆事件；{@code withSnapshot} 為 true 時一併記下當下的內容。 */
    @Transactional
    public void record(Wish wish, WishEventType type, UUID actorId, String note, boolean withSnapshot) {
        events.save(WishEvent.by(wish.getId(), type, actorId, note,
                withSnapshot ? write(snapshotOf(wish)) : null));
    }

    public WishSnapshot snapshotOf(Wish wish) {
        List<String> letterIds = attachments.wishLetters(wish.getId()).stream()
                .map(AttachmentView::id).map(UUID::toString).toList();
        return WishSnapshot.of(wish, letterIds);
    }

    /** 下架修改之後，內容完全沒變、而且最近的狀態變化就是那次下架，才能直接恢復上架。 */
    @Transactional(readOnly = true)
    public boolean canRestore(Wish wish) {
        if (wish.getStatus() != WishStatus.DRAFT || wish.getPublishedAt() == null) {
            return false;
        }
        Optional<WishEvent> latest = events.findFirstByWishIdAndEventTypeInOrderByCreatedAtDescIdDesc(
                wish.getId(), LIFECYCLE);
        if (latest.isEmpty() || latest.get().getEventType() != WishEventType.TAKEN_DOWN_FOR_EDIT) {
            return false;
        }
        WishSnapshot before = read(latest.get().getSnapshot());
        return before != null && before.equals(snapshotOf(wish));
    }

    @Transactional(readOnly = true)
    public List<WishHistoryItem> history(UUID wishId) {
        return events.findByWishIdOrderByCreatedAtAscIdAsc(wishId).stream().map(WishHistoryItem::from).toList();
    }

    /** 與上次核准的版本比較；從來沒有核准過（沒有快照）就回 null。 */
    @Transactional(readOnly = true)
    public WishChanges changesSinceLastApproval(Wish wish) {
        Optional<WishEvent> approved = events.findFirstByWishIdAndEventTypeInOrderByCreatedAtDescIdDesc(
                wish.getId(), EnumSet.of(WishEventType.APPROVED));
        WishSnapshot before = approved.map(WishEvent::getSnapshot).map(this::read).orElse(null);
        if (before == null) {
            return null;
        }
        WishSnapshot now = snapshotOf(wish);
        List<FieldChange> fields = new ArrayList<>();
        add(fields, "願望標題", before.title(), now.title());
        add(fields, "孩子的暱稱", before.childAlias(), now.childAlias());
        add(fields, "孩子的年齡", ageLabel(before.ageRange()), ageLabel(now.ageRange()));
        add(fields, "願望描述", before.description(), now.description());
        return new WishChanges(approved.get().getCreatedAt(), fields,
                before.letterIds().size(), now.letterIds().size(),
                !Objects.equals(before.letterIds(), now.letterIds()));
    }

    private static void add(List<FieldChange> fields, String field, String before, String after) {
        if (!Objects.equals(blankToNull(before), blankToNull(after))) {
            fields.add(new FieldChange(field, before == null ? "" : before, after == null ? "" : after));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String ageLabel(String name) {
        return name == null ? null : AgeRange.valueOf(name).getLabel();
    }

    private String write(WishSnapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化願望快照", e);
        }
    }

    private WishSnapshot read(String text) {
        if (text == null) {
            return null;
        }
        try {
            return json.readValue(text, WishSnapshot.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
