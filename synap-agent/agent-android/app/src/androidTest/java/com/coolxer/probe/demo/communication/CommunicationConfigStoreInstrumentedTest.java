package com.coolxer.probe.demo.communication;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class CommunicationConfigStoreInstrumentedTest {
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
    public void missingPreferencesUseBuildDefaults() {
        CommunicationConfig defaults = new CommunicationConfig(
                "https://default.example.com", "default-app", "default-key", "default-anchor");

        assertEquals(defaults, CommunicationConfigStore.load(context, defaults));
    }

    @Test
    public void savedValuesOverrideBuildDefaults() {
        CommunicationConfig defaults = new CommunicationConfig(
                "https://default.example.com", "default-app", "default-key", "default-anchor");
        CommunicationConfig saved = new CommunicationConfig(
                "https://saved.example.com", "saved-app", "saved-key", "saved-anchor");

        assertTrue(CommunicationConfigStore.save(context, saved));
        assertEquals(saved, CommunicationConfigStore.load(context, defaults));
    }

    private void clearPreferences() {
        context.getSharedPreferences(CommunicationConfigStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
}
