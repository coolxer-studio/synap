package com.coolxer.probe.demo.communication;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.appcompat.app.AppCompatActivity;

import com.coolxer.probe.demo.MainActivity;

public class RestartActivity extends AppCompatActivity {
    private static final String EXTRA_PROCESS_ID = "main_process_id";
    private static final long RESTART_DELAY_MILLIS = 250L;

    public static void restart(Activity activity) {
        Intent intent = new Intent(activity, RestartActivity.class)
                .putExtra(EXTRA_PROCESS_ID, Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mainProcessId = getIntent().getIntExtra(EXTRA_PROCESS_ID, -1);
        if (mainProcessId > 0 && mainProcessId != Process.myPid()) {
            Process.killProcess(mainProcessId);
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::startMainProcess,
                RESTART_DELAY_MILLIS);
    }

    private void startMainProcess() {
        Intent restartIntent = Intent.makeRestartActivityTask(
                new ComponentName(this, MainActivity.class));
        startActivity(restartIntent);
        finishAndRemoveTask();
    }
}
