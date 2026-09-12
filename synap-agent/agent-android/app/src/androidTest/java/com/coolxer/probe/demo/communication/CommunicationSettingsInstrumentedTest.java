package com.coolxer.probe.demo.communication;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.coolxer.probe.demo.BuildConfig;
import com.coolxer.probe.demo.R;
import com.coolxer.probe.demo.ui.tool.ToolActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CommunicationSettingsInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearPreferences();
    }

    @After
    public void tearDown() {
        clearPreferences();
    }

    @Test
    public void toolEntryOpensCommunicationSettings() {
        ActivityScenario<ToolActivity> scenario = ActivityScenario.launch(ToolActivity.class);
        try {
            scenario.onActivity(activity -> assertTrue(activity
                    .findViewById(R.id.button_communication_settings).performClick()));
            waitForResumedActivity(CommunicationSettingsActivity.class);
            onView(withId(R.id.edit_communication_base_url)).check(matches(isDisplayed()));
        } finally {
            scenario.close();
        }
    }

    @Test
    public void firstOpenUsesBuildConfigDefaults() {
        ActivityScenario<CommunicationSettingsActivity> scenario =
                ActivityScenario.launch(CommunicationSettingsActivity.class);
        try {
            onView(withId(R.id.edit_communication_base_url))
                    .check(matches(withText(BuildConfig.SYNAP_SC_BASE_URL)));
            onView(withId(R.id.edit_communication_app_id))
                    .check(matches(withText(BuildConfig.SYNAP_SC_APP_ID)));
            onView(withId(R.id.edit_communication_server_key_id))
                    .check(matches(withText(BuildConfig.SYNAP_SC_SERVER_KEY_ID)));
            onView(withId(R.id.edit_communication_trust_anchor))
                    .check(matches(withText(BuildConfig.SYNAP_SC_TRUST_ANCHOR)));
        } finally {
            scenario.close();
        }
    }

    @Test
    public void invalidSaveShowsFieldErrorAndKeepsPreviousConfig() throws Exception {
        CommunicationConfig previous = validConfig("https://old.example.com");
        assertTrue(CommunicationConfigStore.save(context, previous));

        ActivityScenario<CommunicationSettingsActivity> scenario =
                ActivityScenario.launch(CommunicationSettingsActivity.class);
        try {
            onView(withId(R.id.edit_communication_base_url)).perform(replaceText("ftp://bad.test"));
            closeSoftKeyboard();
            onView(withId(R.id.button_save_communication_settings)).perform(scrollTo(), click());
            onView(withId(R.id.edit_communication_base_url))
                    .check(matches(hasErrorText("请输入不含用户信息、查询参数或片段的有效 URL")));
            assertEquals(previous, CommunicationConfigStore.load(context));
        } finally {
            scenario.close();
        }
    }

    @Test
    public void validSavePersistsAllFieldsAndShowsRestartDialog() throws Exception {
        CommunicationConfig expected = validConfig("http://new.example.com/api");
        ActivityScenario<CommunicationSettingsActivity> scenario =
                ActivityScenario.launch(CommunicationSettingsActivity.class);
        try {
            onView(withId(R.id.edit_communication_base_url))
                    .perform(replaceText("  " + expected.getBaseUrl() + "  "));
            onView(withId(R.id.edit_communication_app_id))
                    .perform(replaceText("  " + expected.getAppId() + "  "));
            onView(withId(R.id.edit_communication_server_key_id))
                    .perform(replaceText("  " + expected.getServerKeyId() + "  "));
            onView(withId(R.id.edit_communication_trust_anchor))
                    .perform(replaceText("  " + expected.getTrustAnchor() + "  "));
            closeSoftKeyboard();
            onView(withId(R.id.button_save_communication_settings)).perform(scrollTo(), click());

            onView(withText(R.string.communication_settings_saved_title))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.communication_settings_restart_now))
                    .check(matches(isDisplayed()));
            assertEquals(expected, CommunicationConfigStore.load(context));
            onView(withText(R.string.communication_settings_restart_later)).perform(click());
        } finally {
            scenario.close();
        }
    }

    private CommunicationConfig validConfig(String baseUrl) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String anchor = Base64.encodeToString(generator.generateKeyPair().getPublic().getEncoded(),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new CommunicationConfig(baseUrl, "synap-android", "server-key-test", anchor);
    }

    private void clearPreferences() {
        context.getSharedPreferences(CommunicationConfigStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private void waitForResumedActivity(Class<? extends Activity> activityClass) {
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean found = new AtomicBoolean(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        found.set(true);
                        return;
                    }
                }
            });
            if (found.get()) return;
            SystemClock.sleep(50L);
        }
        fail("Activity did not reach RESUMED: " + activityClass.getName());
    }
}
