package com.coolxer.probe.demo.ui.identify;

import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.coolxer.probe.demo.MainActivity;
import com.coolxer.probe.demo.R;
import com.coolxer.probe.demo.databinding.FragmentIdentifyBinding;
import com.nex3z.flowlayout.FlowLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class IdentifyFragment extends Fragment {

    private IdentifyViewModel identifyViewModel;
    private FragmentIdentifyBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentIdentifyBinding.inflate(inflater, container, false);
        setInstructionDialog();
        setData();
        View root = binding.getRoot();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void setInstructionDialog(){
        binding.highInstruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                instructionDialog("说明","高风险标记需要及时处理");
            }
        });
        binding.middleInstruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                instructionDialog("说明","中风险标记需要重点关注");
            }
        });
        binding.lowInstruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                instructionDialog("说明","低风险标记");
            }
        });
        binding.normalInstruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                instructionDialog("说明","常规险标无风险");
            }
        });
        binding.otherInstruction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                instructionDialog("说明","未分类的标记");
            }
        });
    }

    private void instructionDialog(String title, String message){
        // 创建AlertDialog.Builder
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.context);
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

    public void setData() {
        identifyViewModel =
                new ViewModelProvider(this).get(IdentifyViewModel.class);

        final FlowLayout labelHigh = binding.labelHigh;
        final FlowLayout labelMedium = binding.labelMedium;
        final FlowLayout labelLow = binding.labelLow;
        final FlowLayout labelNormal = binding.labelNormal;
        final FlowLayout labelOther = binding.labelOther;


        identifyViewModel.getResponseData().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                try {
                    JSONObject ratingJsonObject = new JSONObject(s);
                    if(ratingJsonObject.optInt("code") == 0){
                        JSONArray dataJsonArray = ratingJsonObject.optJSONArray("data");
                        if(dataJsonArray != null){
                            for (int i = 0; i < dataJsonArray.length(); i++) {
                                JSONObject itemJson = dataJsonArray.optJSONObject(i);
                                JSONArray valueList = itemJson.optJSONArray("value_list");
                                switch (itemJson.optString("type")){
                                    case "high":
                                        addViewsToFlowLayout(labelHigh,valueList,R.color.red_200);
                                        break;
                                    case "medium":
                                        addViewsToFlowLayout(labelMedium,valueList,R.color.yellow_700);
                                        break;
                                    case "low":
                                        addViewsToFlowLayout(labelLow,valueList,R.color.green_700);
                                        break;
                                    case "normal":
                                        addViewsToFlowLayout(labelNormal,valueList,R.color.green_700);
                                        break;
                                    case "other":
                                        addViewsToFlowLayout(labelOther,valueList,R.color.green_700);
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        identifyViewModel.postData();
    }

    private void addViewsToFlowLayout(FlowLayout flowLayout,JSONArray valueList,@ColorRes int colorId){
        flowLayout.removeAllViews();
        for (int i = 0; i < valueList.length(); i++) {
            flowLayout.addView(createTextView(valueList.optString(i),colorId));
        }
    }

    private TextView createTextView(String text,@ColorRes int colorId){
        // 创建TextView
        TextView textView = new TextView(MainActivity.context);
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setText(text);

        // 设置内边距
        int paddingInDp = 5;
        final float scale = getResources().getDisplayMetrics().density;
        int paddingInPx = (int) (paddingInDp * scale + 0.5f);
        textView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx);

        // 设置背景（圆角绿色背景）
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(8 * scale); // 圆角半径，根据需要调整
        drawable.setColor(ContextCompat.getColor(getContext(), colorId)); // 颜色
        textView.setBackground(drawable);
        return textView;
    }
}