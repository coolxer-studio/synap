package com.coolxer.probe.demo.ui.identify;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.coolxer.probe.CTProbe;

public class IdentifyViewModel extends ViewModel {

    private MutableLiveData<String> responseData;

    public IdentifyViewModel() {
        responseData = new MutableLiveData<>();
    }

    public void postData(){
        new Thread(){
            @Override
            public void run() {
                super.run();
                String labelResponse = CTProbe.getInstance().labelInfo();
                if(labelResponse!=null){
                    responseData.postValue(labelResponse);
                }
            }
        }.start();
    }

    public MutableLiveData<String> getResponseData() {
        return responseData;
    }
}