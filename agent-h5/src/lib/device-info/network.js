const ipToNumber = function (ips) {

    const ipv4ToNum = (ip) => {
        const allNum = ip.split(".");
        if (allNum.length !== 4) return -1
        return parseInt(allNum[0]) * 256 * 256 * 256 +
            parseInt(allNum[1]) * 256 * 256 +
            parseInt(allNum[2]) * 256 +
            parseInt(allNum[3]);
    }
    const ip_list = [[], [], []];
    ips.forEach((ip) => {
        if (ip.match(/^(127\.0\.0\.1)|(localhost)|(10\.\d{1,3}\.\d{1,3}\.\d{1,3})|(172\.((1[6-9])|(2\d)|(3[01]))\.\d{1,3}\.\d{1,3})|(192\.168\.\d{1,3}\.\d{1,3})$/)) {
            const ip_num = ipv4ToNum(ip)
            if (ip_num > 0) ip_list[0].push(ip_num)
        } else if (ip.match(/^[a-f0-9]{1,4}(:[a-f0-9]{1,4}){7}$/)) {
            ip_list[2].push(ip.replace(':', '-'))
        } else {
            const ip_num = ipv4ToNum(ip)
            if (ip_num > 0) ip_list[1].push(ip_num)
        }
    })
    // console.log('ip_list:',ip_list)
    if (!ip_list.some((e) => { return e.length })) return [[], [], []]
    return ip_list
}

export default {
    /**
     * 网络类型
     * @returns 网络类型
     */
    type: function () {
        if (navigator.connection) {
            if (navigator.connection.effectiveType === "4g") {
                return "4G";
            } else if (navigator.connection.effectiveType === "wifi") {
                return "Wi-Fi";
            } else {
                return "other";
            }
        } else {
            return "unknown";
        }
    },
    /**
     * 网络信息 
     * @returns 网络信息
     */
    info: function () {
        let info = 'unknown|unknown|unknown|unknown';
        let effectiveType = 'unknown';
        let downlinkMax = 'unknown';
        const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection
        if (conn) {
            if (conn.type) {
                effectiveType = conn.type
                downlinkMax = conn.downlinkMax || 'unknown'
            }
            const {
                rtt,
                saveData
            } = conn;

            info = [effectiveType, downlinkMax, rtt, saveData].join('|');
        }
        return info;
    },

    /**
     * 获取IP地址
     */
    lanIp: function () {
        const f = ["RTCPeerConnection", "webkitRTCPeerConnection", "mozRTCPeerConnection", "RTCIceGatherer", "RtpDataChannels"];
        const hasR = function () {
            for (let l = 0; l < f.length; l++) {
                if (f[l] in window) {
                    return true;
                }
            }
            return false;
        };
        if (hasR() && !new RegExp("edge").test(navigator.userAgent)) {
            const result = [];
            const resultSign = {};
            let RTC = window[f[0]] || window[f[2]] || window[f[1]];
            function REGO(x) {
                //match just the IP address
                const ipTest = /([0-9]{1,3}(\.[0-9]{1,3}){3}|[a-f0-9]{1,4}(:[a-f0-9]{1,4}){7})/;
                const o = ipTest.exec(x);
                if (!o) return;
                const k = o[1];
                //remove duplicates
                if (resultSign[k] === void 0) result.push(k);
                resultSign[k] = true
            }
            if (!RTC) {
                const x = document.createElement('iframe');
                x.id = 'bs-Iframe';
                x.style.display = 'none';
                x.sandbox = 'allow-same-origin';
                document.body.appendChild(x);
                const z = x.contentWindow;
                RTC = z[f[0]] || z[f[2]] || z[f[1]];
            }
            const chParam = {};
            chParam.RtpDataChannels = false;
            const opParam = {};
            //minimal requirements for data connection
            opParam.optional = [chParam];
            const urlParam = {};
            urlParam.urls = ["stun:stun.ideasip.com", "stun:stun.ekiga.net"];
            const serParam = {};
            serParam.iceServers = [urlParam];
            serParam.sdpSemantics = 'unified-plan';
            try {
                const insRTC = new RTC(serParam, opParam);
                const channel = insRTC.createDataChannel('');
                //create an offer sdp
                insRTC.createOffer().then(function (e) {
                    //trigger the stun server request
                    return insRTC.setLocalDescription(e);
                }).then(function () {
                    //wait for a while to let everything done
                    setTimeout(function () {
                        //read candidate info from local description
                        const o = insRTC.localDescription.sdp.split('\x0a');
                        o.forEach(function (e) {
                            if (e.indexOf('a=candidate:') === 0 || e.indexOf('c=IN') === 0) REGO(e);
                        });
                        channel.close();
                        insRTC.close();
                        const bFrame = document.getElementById('bs-Iframe');
                        if (bFrame) bFrame.parentNode.removeChild(bFrame);
                    }, 1000);
                }).catch(function (e) {
                    const bFrame = document.getElementById('bs-Iframe');
                    if (bFrame) bFrame.parentNode.removeChild(bFrame);
                    channel.close();
                    insRTC.close();
                });
            } catch (e) {
                console.error('no rtc:', e.toString())
            }
        }
    }
}
