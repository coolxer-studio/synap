package com.coolxer.model.cross;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class APIConfig {

    @JsonProperty("id")
    private String id;

    @JsonProperty("api_name")
    private String apiName;
    
    @JsonProperty("api_url")
    private String apiUrl;
}
