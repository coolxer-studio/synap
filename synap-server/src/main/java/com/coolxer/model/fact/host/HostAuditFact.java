package com.coolxer.model.fact.host;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.ValueSet;
import com.coolxer.model.fact.ValueType;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
public class HostAuditFact extends AbstractFact {
    public static final int EVENT_COLUMNS = 18;
    public static final int MAX_EVENTS = 100;
    public static final int MAX_BATCH_BYTES = 128 * 1024;
    public static final int MAX_DETAIL_BYTES = 4 * 1024;

    private static final Set<String> CATEGORIES = Set.of("device", "session", "account", "config_file",
            "peripheral", "network", "process", "business_file", "scheduled_task", "capability");
    private static final Set<String> ACTIONS = Set.of("start", "shutdown", "unclean_shutdown", "login", "logout",
            "created", "deleted", "modified", "permission_changed", "attached", "detached", "established",
            "terminated", "started", "running", "executed", "degraded", "policy_changed");
    private static final Set<String> RESULTS = Set.of("success", "failure", "observed", "detected", "unavailable");
    private static final Set<String> PROTOCOLS = Set.of("", "tcp", "udp");
    private static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");

    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<Object> events;

    public HostAuditFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, String.valueOf(factMap.get("buildDate")), header, common);
        validate(factMap);
        this.batchId = String.valueOf(factMap.get("batchId"));
        this.events = new ArrayList<>((List<?>) factMap.get("events"));
    }

    public static void validate(Map<String, Object> factMap) {
        if (factMap == null || !"AuditData".equals(factMap.get("type"))) {
            throw new IllegalArgumentException("invalid AuditData type");
        }
        String batchId = String.valueOf(factMap.get("batchId"));
        try { UUID.fromString(batchId); }
        catch (RuntimeException error) { throw new IllegalArgumentException("invalid AuditData batchId"); }
        if (!(factMap.get("events") instanceof List<?> rows) || rows.isEmpty() || rows.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("AuditData events must contain 1..100 rows");
        }
        for (Object value : rows) {
            if (!(value instanceof List<?> row) || row.size() != EVENT_COLUMNS) {
                throw new IllegalArgumentException("AuditData event must contain exactly 18 columns");
            }
            ArrayList<String> columns = new ArrayList<>(EVENT_COLUMNS);
            for (Object column : row) {
                if (!(column instanceof String)) throw new IllegalArgumentException("AuditData columns must be strings");
                columns.add((String) column);
            }
            if (!EVENT_ID.matcher(columns.get(0)).matches()) throw new IllegalArgumentException("invalid audit eventId");
            try { OffsetDateTime.parse(columns.get(1)); }
            catch (DateTimeParseException error) { throw new IllegalArgumentException("invalid audit eventTime"); }
            if (!CATEGORIES.contains(columns.get(2))) throw new IllegalArgumentException("invalid audit category");
            if (!ACTIONS.contains(columns.get(3))) throw new IllegalArgumentException("invalid audit action");
            if (!RESULTS.contains(columns.get(4))) throw new IllegalArgumentException("invalid audit result");
            if (!columns.get(8).isEmpty()) {
                try { if (Long.parseLong(columns.get(8)) < 0) throw new NumberFormatException(); }
                catch (NumberFormatException error) { throw new IllegalArgumentException("invalid audit pid"); }
            }
            if (!PROTOCOLS.contains(columns.get(12))) throw new IllegalArgumentException("invalid audit protocol");
            if (!(columns.get(13).isEmpty() || "SHA-256".equals(columns.get(13)))) throw new IllegalArgumentException("invalid digest algorithm");
            if ("SHA-256".equals(columns.get(13)) && !SHA256.matcher(columns.get(14)).matches()) throw new IllegalArgumentException("invalid SHA-256 digest");
            if (columns.get(15).isBlank() || columns.get(15).length() > 128) throw new IllegalArgumentException("invalid audit source");
            if (columns.get(16).isBlank() || columns.get(16).length() > 128) throw new IllegalArgumentException("invalid audit bootId");
            if (columns.get(17).getBytes(StandardCharsets.UTF_8).length > MAX_DETAIL_BYTES) {
                throw new IllegalArgumentException("audit detail exceeds 4 KiB");
            }
        }
        try {
            if (JacksonUtils.toJson(rows).getBytes(StandardCharsets.UTF_8).length > MAX_BATCH_BYTES) {
                throw new IllegalArgumentException("AuditData batch exceeds 128 KiB");
            }
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("AuditData events are not serializable");
        }
    }
}
