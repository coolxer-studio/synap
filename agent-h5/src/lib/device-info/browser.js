
var countTruthy = function (values) {
  return values.reduce(function (sum, value) {
    return sum + (value ? 1 : 0);
  }, 0);
}
/*
   * Functions to help with browser features
   */
var w = window;
var n = navigator;
var d = document;

/**
 * Checks whether the browser is based on Trident (the Internet Explorer engine) without using user-agent.
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isTrident() {
  // The properties are checked to be in IE 10, IE 11 and not to be in other browsers in October 2020
  return (countTruthy([
    'MSCSSMatrix' in w,
    'msSetImmediate' in w,
    'msIndexedDB' in w,
    'msMaxTouchPoints' in n,
    'msPointerEnabled' in n,
  ]) >= 4);
}

/**
 * Checks whether the browser is based on EdgeHTML (the pre-Chromium Edge engine) without using user-agent.
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isEdgeHTML() {
  // Based on research in October 2020
  return (countTruthy(['msWriteProfilerMark' in w, 'MSStream' in w, 'msLaunchUri' in n, 'msSaveBlob' in n]) >= 3 &&
    !isTrident());
}

/**
 * Checks whether the browser is based on Chromium without using user-agent.
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isChromium() {
  // Based on research in October 2020. Tested to detect Chromium 42-86.
  return (countTruthy([
    'webkitPersistentStorage' in n,
    'webkitTemporaryStorage' in n,
    n.vendor && n.vendor.indexOf('Google') === 0,
    'webkitResolveLocalFileSystemURL' in w,
    'BatteryManager' in w,
    'webkitMediaStream' in w,
    'webkitSpeechGrammar' in w,
  ]) >= 5);
}

/**
 * Checks whether the browser is based on mobile or desktop Safari without using user-agent.
 * All iOS browsers use WebKit (the Safari engine).
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isWebKit() {
  // Based on research in September 2020
  return (countTruthy([
    'ApplePayError' in w,
    'CSSPrimitiveValue' in w,
    'Counter' in w,
    n.vendor && n.vendor.indexOf('Apple') === 0,
    'getStorageUpdates' in n,
    'WebKitMediaKeys' in w,
  ]) >= 4);
}

/**
 * Checks whether the WebKit browser is a desktop Safari.
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isDesktopSafari() {
  return (countTruthy([
    'safari' in w,
    !('DeviceMotionEvent' in w),
    !('ongestureend' in w),
    !('standalone' in n),
  ]) >= 3);
}

/**
 * Checks whether the browser is based on Gecko (Firefox engine) without using user-agent.
 *
 * Warning for package users:
 * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
 */
function isGecko() {
  var _a;
  // Based on research in September 2020
  return (countTruthy([
    'buildID' in n,
    ((_a = d.documentElement) === null || _a === void 0 ? void 0 : _a.style) && 'MozAppearance' in d.documentElement.style,
    'MediaRecorderErrorEvent' in w,
    'mozInnerScreenX' in w,
    'CSSMozDocumentRule' in w,
    'CanvasCaptureMediaStream' in w,
  ]) >= 4);
}

/**
 * Checks whether the browser is based on Chromium version ≥86 without using user-agent.
 * It doesn't check that the browser is based on Chromium, there is a separate function for this.
 */
function isChromium86OrNewer() {
  // Checked in Chrome 85 vs Chrome 86 both on desktop and Android
  return (countTruthy([
    !('MediaSettingsRange' in w),
    'RTCEncodedAudioFrame' in w,
    '' + w.Intl === '[object Intl]',
    '' + w.Reflect === '[object Reflect]',
  ]) >= 3);
}

/**
 * Checks whether the browser is based on WebKit version ≥606 (Safari ≥12) without using user-agent.
 * It doesn't check that the browser is based on WebKit, there is a separate function for this.
 *
 * @link https://en.wikipedia.org/wiki/Safari_version_history#Release_history Safari-WebKit versions map
 */
function isWebKit606OrNewer() {
  // Checked in Safari 9–14
  return (countTruthy([
    'DOMRectList' in w,
    'RTCPeerConnectionIceEvent' in w,
    'SVGGeometryElement' in w,
    'ontransitioncancel' in w,
  ]) >= 3);
}



export default {
  type: function () {
    return [
      isTrident() ? 1 : 0,
      isEdgeHTML() ? 1 : 0,
      isChromium() ? 1 : 0,
      isWebKit() ? 1 : 0,
      isDesktopSafari() ? 1 : 0,
      isGecko() ? 1 : 0,
      isChromium86OrNewer() ? 1 : 0,
      isWebKit606OrNewer() ? 1 : 0
    ]
  },
  // 判断播放支持类型
  playType: function () {
    let t = [false, false, false]
    const a = document.createElement('video');

    try {
      if (a.canPlayType) {
        t = [a.canPlayType('video/ogg; codecs="theora"'),
        a.canPlayType('video/mp4; codecs="avc1.42E01E"'),
        a.canPlayType('video/webm; codecs="vp8, vorbis"'),]
      }
    } catch (e) {
      console.error(e)
    }
    return t
  },


  /**
   *  获取输入设备,需要权限
   */
  inputDevice: function () {
    const inputDevice = [];
    if (navigator.mediaDevices === void 0) {
      navigator.mediaDevices = {};
    }
    if (navigator.mediaDevices.getUserMedia === void 0) {
      navigator.mediaDevices.getUserMedia = function (constraints) {
        const getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia || navigator.oGetUserMedia;
        if (!getUserMedia) {

          return Promise.reject(new Error('getUserMedia is not implemented in this browser'));
        }
        return new Promise(function (resolve, reject) {
          getUserMedia.call(navigator, constraints, resolve, reject);
        });
      }
    }
    if (window.stream) {
      window.stream.getTracks().forEach(track => {
        track.stop();
      });
    }
    function getDeviceAudio(stream) {
      let tracks = stream.getTracks();
      for (let i = 0; i < tracks.length; i++) {
        tracks[i].stop();
      }
      try {
        navigator.mediaDevices.enumerateDevices().then((devices) => {
          devices.forEach((value, index) => {
            inputDevice.push({
              kind: value.kind,
              label: value.label,
              deviceId: value.deviceId,
              groupId: value.groupId,
            })
          })
        })
      } catch (e) { }
    }
    if (navigator.mediaDevices.getUserMedia) {
      navigator.mediaDevices.getUserMedia({
        audio: true,
        video: true
      }).then((stream) => {
        getDeviceAudio(stream)
      }).catch((err) => { })
    }
  }
}
