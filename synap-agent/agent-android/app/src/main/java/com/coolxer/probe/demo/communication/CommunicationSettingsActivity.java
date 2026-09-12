package com.coolxer.probe.demo.communication;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.coolxer.probe.demo.R;

public class CommunicationSettingsActivity extends AppCompatActivity {
    private EditText baseUrlEdit;
    private EditText appIdEdit;
    private EditText serverKeyIdEdit;
    private EditText trustAnchorEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication_settings);

        baseUrlEdit = findViewById(R.id.edit_communication_base_url);
        appIdEdit = findViewById(R.id.edit_communication_app_id);
        serverKeyIdEdit = findViewById(R.id.edit_communication_server_key_id);
        trustAnchorEdit = findViewById(R.id.edit_communication_trust_anchor);
        findViewById(R.id.button_save_communication_settings).setOnClickListener(
                view -> saveSettings());

        showConfig(CommunicationConfigStore.load(this));
    }

    private void showConfig(CommunicationConfig config) {
        baseUrlEdit.setText(config.getBaseUrl());
        appIdEdit.setText(config.getAppId());
        serverKeyIdEdit.setText(config.getServerKeyId());
        trustAnchorEdit.setText(config.getTrustAnchor());
    }

    private void saveSettings() {
        clearErrors();
        CommunicationConfig config = new CommunicationConfig(
                text(baseUrlEdit),
                text(appIdEdit),
                text(serverKeyIdEdit),
                text(trustAnchorEdit));
        CommunicationConfigValidator.Result validation =
                CommunicationConfigValidator.validate(config);
        if (!validation.isValid()) {
            showErrors(validation);
            return;
        }
        if (!CommunicationConfigStore.save(this, config)) {
            Toast.makeText(this, R.string.communication_settings_save_failed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        showRestartDialog();
    }

    private void showErrors(CommunicationConfigValidator.Result validation) {
        baseUrlEdit.setError(validation.getError(CommunicationConfigValidator.Field.BASE_URL));
        appIdEdit.setError(validation.getError(CommunicationConfigValidator.Field.APP_ID));
        serverKeyIdEdit.setError(
                validation.getError(CommunicationConfigValidator.Field.SERVER_KEY_ID));
        trustAnchorEdit.setError(
                validation.getError(CommunicationConfigValidator.Field.TRUST_ANCHOR));

        EditText firstInvalid = firstInvalidField(validation);
        if (firstInvalid != null) {
            firstInvalid.requestFocus();
        }
    }

    private EditText firstInvalidField(CommunicationConfigValidator.Result validation) {
        if (validation.getError(CommunicationConfigValidator.Field.BASE_URL) != null) {
            return baseUrlEdit;
        }
        if (validation.getError(CommunicationConfigValidator.Field.APP_ID) != null) {
            return appIdEdit;
        }
        if (validation.getError(CommunicationConfigValidator.Field.SERVER_KEY_ID) != null) {
            return serverKeyIdEdit;
        }
        if (validation.getError(CommunicationConfigValidator.Field.TRUST_ANCHOR) != null) {
            return trustAnchorEdit;
        }
        return null;
    }

    private void clearErrors() {
        baseUrlEdit.setError(null);
        appIdEdit.setError(null);
        serverKeyIdEdit.setError(null);
        trustAnchorEdit.setError(null);
    }

    private void showRestartDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.communication_settings_saved_title)
                .setMessage(R.string.communication_settings_saved_message)
                .setNegativeButton(R.string.communication_settings_restart_later, null)
                .setPositiveButton(R.string.communication_settings_restart_now,
                        (dialog, which) -> RestartActivity.restart(this))
                .show();
    }

    private static String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
