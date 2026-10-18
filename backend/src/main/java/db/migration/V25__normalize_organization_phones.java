package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.onlinesanta.common.PhoneFormat;

/**
 * 既有機構的電話改存加了 dash 的格式（1018 新版 SPEC 13.4）。
 *
 * <p>規則寫在 {@link PhoneFormat}（SQL 很難表達區碼規則）。<strong>只改格式合法的值</strong>：
 * 看不懂的舊資料（有分機、缺碼、夾雜文字）原樣保留，不能因為格式化而弄丟資訊；
 * 機構日後在機構設定儲存時，驗證會要求改成合法格式。
 */
public class V25__normalize_organization_phones extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        record Row(UUID id, String contactPhone, String mobilePhone) {
        }
        List<Row> rows = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, contact_phone, mobile_phone FROM organizations")) {
            while (result.next()) {
                rows.add(new Row(result.getObject(1, UUID.class), result.getString(2), result.getString(3)));
            }
        }
        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE organizations SET contact_phone = ?, mobile_phone = ? WHERE id = ?")) {
            for (Row row : rows) {
                String landline = normalize(row.contactPhone(), PhoneFormat::landline);
                String mobile = normalize(row.mobilePhone(), PhoneFormat::mobile);
                if (java.util.Objects.equals(landline, row.contactPhone())
                        && java.util.Objects.equals(mobile, row.mobilePhone())) {
                    continue;
                }
                update.setString(1, landline);
                update.setString(2, mobile);
                update.setObject(3, row.id());
                update.executeUpdate();
            }
        }
    }

    private static String normalize(String value, Function<String, Optional<String>> format) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return format.apply(value).orElse(value);
    }
}
