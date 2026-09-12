package com.coolxer.service;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.agenda.AgendaLevelEnum;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.vo.RiskAssessment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RiskAssessmentService {
    private final Map<String, State> memory = new ConcurrentHashMap<>();
    private final Map<String, AccountState> accounts = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final Duration ttl;
    private final Duration halfLife;
    private final int maxEntries;

    public RiskAssessmentService(ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${synap.risk.store:memory}") String store,
            @Value("${synap.risk.ttl:PT30M}") Duration ttl,
            @Value("${synap.risk.half-life:PT15M}") Duration halfLife,
            @Value("${synap.risk.memory.max-entries:100000}") int maxEntries) {
        this.redis = redisProvider.getIfAvailable(); this.redisEnabled = "redis".equalsIgnoreCase(store) && redis != null;
        this.ttl = ttl; this.halfLife = halfLife; this.maxEntries = maxEntries;
    }

    public synchronized RiskAssessment assess(AbstractFact fact, List<Agenda> agendas) {
        String platform = fact.getCommon().getPlatform().toLowerCase();
        String key = platform + ":" + fact.getCommon().getAppId() + ":" + fact.getCommon().getStartId();
        State state = read(key); long now = System.currentTimeMillis();
        double decay = state.updatedAt <= 0 ? 0 : Math.pow(0.5, (double) (now - state.updatedAt) / Math.max(1, halfLife.toMillis()));
        state.score *= decay;
        LinkedHashSet<String> currentReasons = new LinkedHashSet<>();
        double added = 0;
        added += correlateAccount(fact, currentReasons, now);
        for (Agenda agenda : agendas == null ? List.<Agenda>of() : agendas) {
            currentReasons.add(reasonCode(agenda.getTag()));
            added += agenda.getLevel() == AgendaLevelEnum.HIGH ? 30 : agenda.getLevel() == AgendaLevelEnum.MEDIUM ? 18
                    : agenda.getLevel() == AgendaLevelEnum.LOW ? 8 : 2;
        }
        if (fact.getSequenceObservation() != null && fact.getSequenceObservation().startsWith("SEQUENCE_")
                && !"SEQUENCE_OK".equals(fact.getSequenceObservation())
                && !"SEQUENCE_NOT_APPLICABLE".equals(fact.getSequenceObservation())) {
            currentReasons.add(fact.getSequenceObservation()); added += "SEQUENCE_REORDERED".equals(fact.getSequenceObservation()) ? 15 : 8;
        }
        if (fact.getClientServerSkewMs() != null && Math.abs(fact.getClientServerSkewMs()) > Duration.ofMinutes(5).toMillis()) {
            currentReasons.add("CLIENT_SERVER_TIME_SKEW"); added += 5;
        }
        String networkCategory = fact.getRequestNetworkCategory() == null ? "" : fact.getRequestNetworkCategory().toLowerCase();
        if (Set.of("tor", "vpn", "proxy", "idc").contains(networkCategory)) {
            currentReasons.add("GATEWAY_NETWORK_" + networkCategory.toUpperCase()); added += 12;
        }
        String reputation = fact.getRequestIpReputation() == null ? "" : fact.getRequestIpReputation().toLowerCase();
        if (Set.of("high-risk", "malicious").contains(reputation)) {
            currentReasons.add("GATEWAY_IP_REPUTATION"); added += 20;
        }
        if (fact.getTransportTrust() == null
                || !fact.getTransportTrust().startsWith("sc1-authenticated/")) {
            currentReasons.add("TRANSPORT_UNAUTHENTICATED");
        }
        state.score = Math.min(100, state.score + added);
        state.reasons.addAll(currentReasons); state.evidenceCount = Math.min(1000, state.evidenceCount + currentReasons.size());
        state.updatedAt = now; write(key, state);
        double confidence = Math.round(Math.min(0.99, state.score / 100 * Math.min(1, state.evidenceCount / 2.0)) * 100) / 100.0;
        String model = "wechat".equals(platform) ? "wechat-risk-1" : "server".equals(platform) ? "server-risk-1" : "h5-risk-1";
        return new RiskAssessment(model, "2026-08-12", Math.round(state.score * 100) / 100.0,
                confidence, new ArrayList<>(state.reasons), state.evidenceCount, true,
                fact.getTransportTrust() == null ? "unauthenticated" : fact.getTransportTrust());
    }

    private State read(String key) {
        if (!redisEnabled) return memory.computeIfAbsent(key, ignored -> new State());
        Map<Object, Object> values = redis.opsForHash().entries(redisKey(key)); State state = new State();
        try { state.score = Double.parseDouble(String.valueOf(values.getOrDefault("score", "0"))); } catch (Exception ignored) { }
        try { state.evidenceCount = Integer.parseInt(String.valueOf(values.getOrDefault("evidence", "0"))); } catch (Exception ignored) { }
        try { state.updatedAt = Long.parseLong(String.valueOf(values.getOrDefault("updatedAt", "0"))); } catch (Exception ignored) { }
        String reasons = String.valueOf(values.getOrDefault("reasons", ""));
        if (!reasons.isBlank()) state.reasons.addAll(List.of(reasons.split(",")));
        return state;
    }

    private double correlateAccount(AbstractFact fact, Set<String> reasons, long now) {
        String userId = fact.getCommon().getUserId();
        if (userId == null || userId.isBlank()) return 0;
        String platform = fact.getCommon().getPlatform().toLowerCase();
        String accountKey = fact.getCommon().getAppId() + ":" + hash(userId);
        String guid = value(fact.getCommon().getGuid()); String ip = value(fact.getRequestIp()); String asn = value(fact.getRequestAsn());
        int activeSessions;
        if (redisEnabled) {
            String key = "synap:" + platform + ":account:" + accountKey;
            Map<Object, Object> previous = redis.opsForHash().entries(key);
            compare(previous.get("guid"), guid, "ACCOUNT_DEVICE_CHANGE", reasons);
            compare(previous.get("ip"), ip, "ACCOUNT_IP_CHANGE", reasons);
            compare(previous.get("asn"), asn, "ACCOUNT_ASN_CHANGE", reasons);
            redis.opsForHash().putAll(key, Map.of("guid", guid, "ip", ip, "asn", asn)); redis.expire(key, ttl);
            String sessionsKey = key + ":sessions"; double activeAfter = now - Duration.ofMinutes(15).toMillis();
            redis.opsForZSet().removeRangeByScore(sessionsKey, Double.NEGATIVE_INFINITY, activeAfter);
            redis.opsForZSet().add(sessionsKey, String.valueOf(fact.getCommon().getStartId()), now); redis.expire(sessionsKey, ttl);
            Long count = redis.opsForZSet().zCard(sessionsKey); activeSessions = count == null ? 1 : count.intValue();
        } else {
            AccountState state = accounts.computeIfAbsent(platform + ":" + accountKey, ignored -> new AccountState());
            compare(state.guid, guid, "ACCOUNT_DEVICE_CHANGE", reasons); compare(state.ip, ip, "ACCOUNT_IP_CHANGE", reasons);
            compare(state.asn, asn, "ACCOUNT_ASN_CHANGE", reasons);
            state.guid = guid; state.ip = ip; state.asn = asn; state.updatedAt = now;
            state.sessions.entrySet().removeIf(value -> value.getValue() < now - Duration.ofMinutes(15).toMillis());
            state.sessions.put(fact.getCommon().getStartId(), now); activeSessions = state.sessions.size();
            if (accounts.size() > maxEntries) accounts.entrySet().stream().min(Comparator.comparingLong(value -> value.getValue().updatedAt))
                    .ifPresent(value -> accounts.remove(value.getKey(), value.getValue()));
        }
        if (activeSessions > 3) reasons.add("ACCOUNT_CONCURRENT_SESSIONS");
        double score = 0;
        if (reasons.contains("ACCOUNT_DEVICE_CHANGE")) score += 8;
        if (reasons.contains("ACCOUNT_IP_CHANGE")) score += 3;
        if (reasons.contains("ACCOUNT_ASN_CHANGE")) score += 5;
        if (reasons.contains("ACCOUNT_CONCURRENT_SESSIONS")) score += 8;
        return score;
    }

    private static void compare(Object previous, String current, String reason, Set<String> reasons) {
        String old = value(previous);
        if (!old.isBlank() && !current.isBlank() && !old.equals(current)) reasons.add(reason);
    }

    private static String value(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "unavailable".equalsIgnoreCase(text) ? "" : text;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 16; index++) result.append(String.format("%02x", digest[index]));
            return result.toString();
        } catch (Exception ignored) { return Integer.toHexString(value.hashCode()); }
    }

    private void write(String key, State state) {
        if (redisEnabled) {
            String redisKey = redisKey(key);
            redis.opsForHash().putAll(redisKey, Map.of("score", String.valueOf(state.score),
                    "evidence", String.valueOf(state.evidenceCount), "updatedAt", String.valueOf(state.updatedAt),
                    "reasons", String.join(",", state.reasons)));
            redis.expire(redisKey, ttl); return;
        }
        if (memory.size() > maxEntries) memory.entrySet().stream().min(Comparator.comparingLong(value -> value.getValue().updatedAt))
                .ifPresent(value -> memory.remove(value.getKey(), value.getValue()));
        long expiredBefore = System.currentTimeMillis() - ttl.toMillis();
        memory.entrySet().removeIf(value -> value.getValue().updatedAt < expiredBefore);
    }

    private static String reasonCode(String tag) {
        if (tag == null) return "RULE_OBSERVATION";
        if (tag.contains("自动化")) return "BEHAVIOR_AUTOMATION_SIGNALS";
        if (tag.contains("完整性")) return "INTEGRITY_SIGNALS";
        if (tag.contains("UA")) return "USER_AGENT_MISMATCH";
        if (tag.contains("代码格式化")) return "CODE_FORMATTING_DETECTED";
        if (tag.contains("enableDebug")) return "WECHAT_DEBUG_ENABLED";
        if (tag.contains("非Release")) return "WECHAT_NON_RELEASE_ENV";
        if (tag.contains("启动场景")) return "WECHAT_AUTOMATION_SCENE";
        if (tag.contains("运行环境")) return "WECHAT_RUNTIME_ENVIRONMENT_SIGNALS";
        if (tag.contains("时钟")) return "CLIENT_CLOCK_ROLLBACK";
        if (tag.contains("标签")) return "CONCURRENT_TABS";
        if (tag.contains("异常")) return "CLIENT_ERROR_BURST";
        if (tag.contains("开发者工具")) return "WECHAT_DEVTOOLS_DETECTED";
        if (tag.contains("调试")) return "DEBUG_ACTIVITY_DETECTED";
        if (tag.contains("网络")) return "NETWORK_FAILURE_BURST";
        if (tag.contains("位置")) return "LOCATION_SPEED_ANOMALY";
        if (tag.contains("域名")) return "ORIGIN_MISMATCH";
        return "RULE_" + Integer.toHexString(tag.hashCode()).toUpperCase();
    }

    private static String redisKey(String key) {
        int separator = key.indexOf(':');
        String platform = separator < 0 ? "h5" : key.substring(0, separator);
        String sessionKey = separator < 0 ? key : key.substring(separator + 1);
        return "synap:" + platform + ":risk:" + sessionKey;
    }
    private static final class State {
        private double score; private int evidenceCount; private long updatedAt;
        private final Set<String> reasons = new LinkedHashSet<>();
    }
    private static final class AccountState {
        private String guid = ""; private String ip = ""; private String asn = ""; private long updatedAt;
        private final Map<Long, Long> sessions = new ConcurrentHashMap<>();
    }
}
