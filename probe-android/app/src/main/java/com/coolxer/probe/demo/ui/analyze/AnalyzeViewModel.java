package com.coolxer.probe.demo.ui.analyze;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.coolxer.probe.CTProbe;

public class AnalyzeViewModel extends ViewModel {

    private MutableLiveData<String> responseData;

    public AnalyzeViewModel() {
        responseData = new MutableLiveData<>();
        responseData.setValue("加载中...");
    }

    public void postData(){
        new Thread(){
            @Override
            public void run() {
                super.run();
                String ratingResponse = CTProbe.getInstance().ratingInfo();
                if(ratingResponse!=null){
                    responseData.postValue(ratingResponse);
                }
            }
        }.start();
    }

    public LiveData<String> getResponseData() {
        return responseData;
    }
}