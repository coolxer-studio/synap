package com.coolxer.probe.demo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import com.coolxer.probe.CTProbe;
import com.coolxer.probe.demo.communication.CommunicationConfig;
import com.coolxer.probe.demo.communication.CommunicationConfigStore;
import com.coolxer.probe.demo.communication.CommunicationConfigValidator;
import com.coolxer.probe.demo.ui.tool.ToolActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.coolxer.probe.demo.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;

    private ActivityMainBinding binding;
    public static Context context;

    private ImageView toolImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏顶部导航栏
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();

        context = this;
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_device, R.id.navigation_analyze, R.id.navigation_identify)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        toolImageView = findViewById(R.id.image_view_tool);
        toolImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.context, ToolActivity.class);
                startActivity(intent);
            }
        });
        // 请求相关权限
        // 请求一组权限
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_SETTINGS,
                Manifest.permission.BATTERY_STATS,
                Manifest.permission.GET_PACKAGE_SIZE,
                Manifest.permission.PACKAGE_USAGE_STATS,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CONTACTS
        };

        // 检查权限是否已被授予
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            int result = PermissionChecker.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            // 所有权限已被授予，执行你的逻辑
            Log.d("demo","所有权限已被授予");
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }

        initializeProbe();
    }

    private void initializeProbe() {
        CommunicationConfig communicationConfig = CommunicationConfigStore.load(this);
        CommunicationConfigValidator.Result validation =
                CommunicationConfigValidator.validate(communicationConfig);
        if (!validation.isValid()) {
            Log.w("agent-android", "Secure communication is not configured; probe is disabled");
            Toast.makeText(this, "安全通信未配置，探针暂未启动", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            com.coolxer.securecommunication.SecureClientConfig securityConfig =
                    communicationConfig.toSecureClientConfig();
            // Production hosts must return a short-lived token from a runtime callback.
            // The demo intentionally has no embedded enrollment credential.
            boolean initialized = CTProbe.initWithContext(this, securityConfig, () -> null);
            if (initialized) {
                CTProbe.getInstance().start();
                Log.d("demo", "start probe");
            }
        } catch (IllegalArgumentException exception) {
            Log.e("agent-android", "Invalid secure communication configuration", exception);
            Toast.makeText(this, "安全通信配置无效，请检查服务地址和信任锚", Toast.LENGTH_LONG).show();
        }
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // 权限请求成功，执行你的逻辑
                // ...
            } else {
                // 用户拒绝了权限请求
                Toast.makeText(this, "部分权限拒绝，某些功能可能受限", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
