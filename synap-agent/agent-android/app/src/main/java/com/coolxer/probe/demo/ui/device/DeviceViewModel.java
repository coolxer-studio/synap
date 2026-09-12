package com.coolxer.probe.demo.ui.device;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.coolxer.probe.CTProbe;

public class DeviceViewModel extends ViewModel {

    private MutableLiveData<String> responseData;

    public DeviceViewModel() {
        responseData = new MutableLiveData<>();
    }

    public void postData(){
        new Thread(){
            @Override
            public void run() {
                super.run();
                CTProbe probe = CTProbe.getInstance();
                if (probe == null) {
                    return;
                }
                String deviceInfoResponse = probe.deviceInfo();
                if(deviceInfoResponse!=null){
                    responseData.postValue(deviceInfoResponse);
                }
            }
        }.start();
    }



    public MutableLiveData<String> getResponseData() {
        return responseData;
    }
}
