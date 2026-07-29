package com.coolxer.probe.demo.ui.tool;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.coolxer.probe.demo.MainActivity;
import com.coolxer.probe.demo.R;


public class ToolActivity extends AppCompatActivity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool);

        // open crash
        findViewById(R.id.button_login).setOnClickListener(this);
        findViewById(R.id.button_login_instruction).setOnClickListener(this);
        findViewById(R.id.button_crash).setOnClickListener(this);
        findViewById(R.id.button_crash_instruction).setOnClickListener(this);
        findViewById(R.id.button_privacy).setOnClickListener(this);
        findViewById(R.id.button_privacy_instruction).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_login:
                Intent intent = new Intent(MainActivity.context, LoginActivity.class);
                startActivity(intent);
                break;
            case R.id.button_login_instruction:
                InstructionDialog("说明","用于模拟登录后上传用户信息场景");
                break;
            case R.id.button_crash:
                intent = new Intent(MainActivity.context, CrashActivity.class);
                startActivity(intent);
                break;
            case R.id.button_crash_instruction:
                InstructionDialog("说明","用于模拟崩溃场景");
                break;
            case R.id.button_privacy:
            case R.id.button_privacy_instruction:
                Toast.makeText(ToolActivity.this, "暂未开通～", Toast.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    private void InstructionDialog(String title, String message){
        // 创建AlertDialog.Builder
        AlertDialog.Builder builder = new AlertDialog.Builder(ToolActivity.this);
        // 设置对话框标题和消息
        builder.setTitle(title);
        builder.setMessage(message);
        // 添加按钮
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 用户点击确定按钮后的处理逻辑
                dialog.dismiss(); // 关闭提示框
            }
        });
        // 创建并显示对话框
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

}