package com.coolxer.probe.demo.ui.device;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.coolxer.probe.demo.databinding.FragmentDeviceBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class DeviceFragment extends Fragment {

    private DeviceViewModel deviceViewModel;
    private FragmentDeviceBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentDeviceBinding.inflate(inflater, container, false);
        setData();
        View root = binding.getRoot();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void setData(){
        deviceViewModel =
                new ViewModelProvider(this).get(DeviceViewModel.class);

        final TextView deviceTextView = binding.textDevice;
        final TextView cpuTextView = binding.textCpu;
        final TextView storageTextView = binding.textStorage;
        final TextView screenTextView = binding.textScreen;
        final TextView networkTextView = binding.textNetwork;
        final TextView batteryTextView = binding.textBattery;
        final TextView otherTextView = binding.textOther;
        final TextView appTextView = binding.textApp;
        deviceViewModel.getResponseData().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                try {
                    JSONObject ratingJsonObject = new JSONObject(s);
                    if(ratingJsonObject.optInt("code") == 0){
                        JSONObject responseData = ratingJsonObject.optJSONObject("data");
                        JSONObject factJson = null;
                        if(responseData != null){
                            factJson = responseData.optJSONObject("fact");
                        }
                        if(factJson != null){
                            deviceTextView.setText(getDataForText(factJson,"device"));
                            cpuTextView.setText(getDataForText(factJson,"cpu"));
                            storageTextView.setText(getDataForText(factJson,"storage"));
                            screenTextView.setText(getDataForText(factJson,"screen"));
                            networkTextView.setText(getDataForText(factJson,"network"));
                            batteryTextView.setText(getDataForText(factJson,"battery"));
                            String otherValue = getDataForText(factJson,"uuid");
                            otherValue += getDataForText(factJson,"prop");
                            otherValue += getDataForText(factJson,"build");
                            otherValue += getDataForText(factJson,"opengl");
                            otherTextView.setText(otherValue);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        deviceViewModel.postData();
    }

    private static String getDataForText(JSONObject dataJson, String itemKey){
        String textValue = "";
        JSONObject itemJson = dataJson.optJSONObject(itemKey);
        Iterator hardwareIterable = itemJson.keys();
        while (hardwareIterable.hasNext()){
            String key = (String) hardwareIterable.next();
            textValue+=key+" : "+itemJson.optString(key)+"\n";
        }
        return textValue;
    }
}