package com.coolxer.probe.demo.ui.tool;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.coolxer.probe.CTProbe;
import com.coolxer.probe.demo.R;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private Button authButton;
    private EditText usernameEdit;
    private EditText passwordEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        authButton = findViewById(R.id.button_authenticate);
        usernameEdit = findViewById(R.id.edit_username);
        passwordEdit = findViewById(R.id.edit_password);

        authButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = usernameEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                if(username.isEmpty() || password.isEmpty()){
                    Toast.makeText(LoginActivity.this, "请填写用户名密码～", Toast.LENGTH_LONG).show();
                }else {
                    JSONObject data = new JSONObject();
                    try {
                        data.put("username",username);
                        data.put("password",password);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    CTProbe.getInstance().userData(username,data);
                    // 假设认证成功
                    Toast.makeText(LoginActivity.this, "["+username+"]登录成功", Toast.LENGTH_LONG).show();
                }
            }
        });

    }
}