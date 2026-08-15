package com.coolxer.probe.demo.communication;

import android.content.Context;
import android.content.SharedPreferences;

import com.coolxer.probe.demo.BuildConfig;

public final class CommunicationConfigStore {
    static final String PREFERENCES_NAME = "agent-android-settings";
    static final String KEY_BASE_URL = "synap_sc_base_url";
    static final String KEY_APP_ID = "synap_sc_app_id";
    static final String KEY_SERVER_KEY_ID = "synap_sc_server_key_id";
    static final String KEY_TRUST_ANCHOR = "synap_sc_trust_anchor";

    private CommunicationConfigStore() { }

    public static CommunicationConfig load(Context context) {
        return load(context, buildDefaults());
    }

    static CommunicationConfig load(Context context, CommunicationConfig defaults) {
        SharedPreferences preferences = preferences(context);
        return new CommunicationConfig(
                value(preferences, KEY_BASE_URL, defaults.getBaseUrl()),
                value(preferences, KEY_APP_ID, defaults.getAppId()),
                value(preferences, KEY_SERVER_KEY_ID, defaults.getServerKeyId()),
                value(preferences, KEY_TRUST_ANCHOR, defaults.getTrustAnchor()));
    }

    public static boolean save(Context context, CommunicationConfig config) {
        return preferences(context).edit()
                .putString(KEY_BASE_URL, config.getBaseUrl())
                .putString(KEY_APP_ID, config.getAppId())
                .putString(KEY_SERVER_KEY_ID, config.getServerKeyId())
                .putString(KEY_TRUST_ANCHOR, config.getTrustAnchor())
                .commit();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static String value(SharedPreferences preferences, String key, String fallback) {
        return preferences.contains(key) ? preferences.getString(key, fallback) : fallback;
    }

    private static CommunicationConfig buildDefaults() {
        return new CommunicationConfig(
                BuildConfig.SYNAP_SC_BASE_URL,
                BuildConfig.SYNAP_SC_APP_ID,
                BuildConfig.SYNAP_SC_SERVER_KEY_ID,
                BuildConfig.SYNAP_SC_TRUST_ANCHOR);
    }
}
