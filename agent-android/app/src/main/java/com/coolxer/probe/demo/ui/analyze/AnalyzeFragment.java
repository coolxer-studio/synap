package com.coolxer.probe.demo.ui.analyze;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.coolxer.probe.demo.R;
import com.coolxer.probe.demo.databinding.FragmentAnalyzeBinding;

import org.json.JSONException;
import org.json.JSONObject;

public class AnalyzeFragment extends Fragment {

    private AnalyzeViewModel analyzeViewModel;
    private FragmentAnalyzeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        analyzeViewModel =
                new ViewModelProvider(this).get(AnalyzeViewModel.class);

        binding = FragmentAnalyzeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView scoreTextView = binding.textScore;
        analyzeViewModel.getResponseData().observe(getViewLifecycleOwner(), new Observer<String>() {
            @SuppressLint("ResourceAsColor")
            @Override
            public void onChanged(@Nullable String s) {
                try {
                    JSONObject ratingJsonObject = new JSONObject(s);
                    if(ratingJsonObject.optInt("code") == 0){
                        JSONObject dataJson = ratingJsonObject.optJSONObject("data");
                        String scoreInfo = dataJson.optString("grade")+"\n("+dataJson.optInt("score")+")";
                        if(scoreInfo.contains("高风险")){
                            scoreTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.red_700));
                        }else if(scoreInfo.contains("中风险")){
                            scoreTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.yellow_700));
                        }else{
                            scoreTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.green_700));
                        }
                        scoreTextView.setText(scoreInfo);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        analyzeViewModel.postData();

        // 使用 Glide 加载和显示 GIF 图像
        Glide.with(this).asGif().load(R.drawable.ic_analyze_background).into(binding.imageViewGif);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}