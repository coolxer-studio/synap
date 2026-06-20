package com.coolxer.model.fact;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**

 */
@Getter
@Slf4j
public class Header {
    private static final String SEPARATOR = "-";

    private String guid;
    private String platform;
    private String appId;
    private String sdkVersion;

    public Header(String commonFactCode) {
        String[] commonFactArray = commonFactCode.split(SEPARATOR);
        this.guid = StringUtils.join(ArrayUtils.subarray(commonFactArray, 0, 5), SEPARATOR);
        this.platform = commonFactArray[5];
        this.appId = commonFactArray[6];
        this.sdkVersion = commonFactArray[7];
    }
}
