package com.coolxer.probe.demo.ui.tool;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.coolxer.probe.demo.MainActivity;
import com.coolxer.probe.demo.R;

import java.util.Random;

public class CrashActivity extends AppCompatActivity {

    static {
        System.loadLibrary("demo");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        findViewById(R.id.btn_java_crash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testJavaCrash();
            }
        });
        findViewById(R.id.btn_c_crash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testCCrash();
            }
        });

        findViewById(R.id.btn_anr).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int type = new Random().nextInt(3);
                Log.d("anr-type",type+"");
                switch (type){
                    case 0:
                        testSleep();
                        break;
                    case 1:
                        testInfiniteLoop();
                        break;
                    case 2:
                        testDeadLock();
                        break;
                    default:
                        Toast.makeText(CrashActivity.this,"unknow", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });
    }


    // 测试验证
    private static void testJavaCrash(){
        String nullStr = null;
        int lenth = nullStr.length();
    }

    private native static int testCCrash();

    private static void testSleep() {
        try {
            Thread.sleep(20 * 1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void testInfiniteLoop() {
        int i = 0;
        while (true) {
            i++;
        }
    }

    private void testDeadLock() {
        new Thread(){
            @Override
            public void run() {
                synchronized (MainActivity.class) {
                    setName("anr-deadLock-thread");
                    int i = 0;
                    while (true) {
                        i++;
                    }
                }
            }
        }.start();

        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (MainActivity.class) {
                    Log.e("anr-deadLock", "正常情况下，这里的逻辑永远不会到达");
                }
            }
        }, 2000);
    }

}