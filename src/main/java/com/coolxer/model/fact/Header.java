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
        if (StringUtils.isBlank(commonFactCode)) throw new IllegalArgumentException("header code is missing");
        String[] commonFactArray = commonFactCode.split(SEPARATOR);
        if (commonFactArray.length != 8) throw new IllegalArgumentException("header code must contain 8 segments");
        this.guid = StringUtils.join(ArrayUtils.subarray(commonFactArray, 0, 5), SEPARATOR);
        this.platform = commonFactArray[5];
        this.appId = commonFactArray[6];
        this.sdkVersion = commonFactArray[7];
    }

    public boolean isConsistentWith(Common common) {
        return common != null && guid.equals(common.getGuid()) && platform.equals(common.getPlatform())
                && appId.equals(String.valueOf(common.getAppId())) && sdkVersion.equals(common.getSdkVersion());
    }
}
