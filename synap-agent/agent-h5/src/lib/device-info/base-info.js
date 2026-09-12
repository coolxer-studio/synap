import CRC from '../crc-core'
export default {
  c: null,
  d: null,
  getUserAgent: function () {
    const ug = navigator.userAgent.split(/[)]/);
    return ug.length > 0 ? ug[0] : ''
  },
  
  getAcceptMimeType: function () {
    var acceptedMimeTypes = [];
    for (var i = 0; i < window.navigator.mimeTypes.length; i++) {
      acceptedMimeTypes.push({
        type: window.navigator.mimeTypes[i]['type'],
        description: window.navigator.mimeTypes[i]['description'],
        suffixes: window.navigator.mimeTypes[i]['suffixes'],
      });
    }
    return acceptedMimeTypes
  },
  
  colorDepth: function () {
    return '' + (window.screen.colorDepth || -1)
  },
  getPixelRatio: function () {
    var mediaQuery;
    // To account for zoom, change to use deviceXDPI instead of systemXDPI
    if (window.screen.systemXDPI !== undefined && window.screen.logicalXDPI !== undefined && window.screen.systemXDPI > window.screen.logicalXDPI) {
      // Only allow for values > 1
      return '' + (window.screen.systemXDPI / window.screen.logicalXDPI);
    } else if (window.devicePixelRatio !== undefined) {
      return "" + window.devicePixelRatio;
    } else if (window.matchMedia) {

      mediaQuery = "(-webkit-min-device-pixel-ratio: 2),\
        (min--moz-device-pixel-ratio: 2),\
        (-o-min-device-pixel-ratio: 2/1),\
        (min-resolution: 2dppx)";
      if (window.matchMedia(mediaQuery).matches) {
        return 2;
      }
      mediaQuery = "(-webkit-min-device-pixel-ratio: 1.5),\
        (min--moz-device-pixel-ratio: 1.5),\
        (-o-min-device-pixel-ratio: 3/2),\
        (min-resolution: 1.5dppx)";
      if (window.matchMedia(mediaQuery).matches) {
        return 1.5;
      }
      mediaQuery = "(-webkit-min-device-pixel-ratio: 0.75),\
        (min--moz-device-pixel-ratio: 0.75),\
        (-o-min-device-pixel-ratio: 3/4),\
        (min-resolution: 0.75dppx)";
      if (window.matchMedia(mediaQuery).matches) {
        return 0.7;
      }
    }
    return '1'
  },
  getNavigatorCpuClass: function () {
    if (navigator.cpuClass) {
      return '' + navigator.cpuClass
    } else {
      return ''
    }
  },
  getNavigatorPlatform: function () {
    // Android Chrome 86 and 87 and Android Firefox 80 and 84 don't mock the platform value when desktop mode is requested
    var platform = navigator.platform;
    // iOS mocks the platform value when desktop version is requested: https://github.com/fingerprintjs/fingerprintjs/issues/514
    // iPad uses desktop mode by default since iOS 13
    // The value is 'MacIntel' on M1 Macs
    // The value is 'iPhone' on iPod Touch
    if (platform === 'MacIntel') {
      if (this.isWebKit() && !this.isDesktopSafari()) {
        return this.isIPad() ? 'iPad' : 'iPhone';
      }
    }
    return platform;
  },
  // getDiskQuota: async function () {
  //   if (navigator.storage && navigator.storage.estimate) {
  //     const { quota } = await navigator.storage.estimate()
  //     return '' + quota
  //   } else {
  //     return '0'
  //   }
  // },

  getDoNotTrack: function () {
    if (navigator.doNotTrack) {
      return navigator.doNotTrack
    } else if (navigator.msDoNotTrack) {
      return navigator.msDoNotTrack
    } else if (window.doNotTrack) {
      return window.doNotTrack
    } else {
      return ''
    }
  },
  getPlugins: function () {
    var plugins;
    if (this.isIE()) {
      plugins = this.getIEPlugins();
    } else {
      plugins = this.getRegularPlugins();
    }
    return plugins;
  },
  isIE: function () {
    if (navigator.appName === 'Microsoft Internet Explorer') {
      return true;
    } else if (navigator.appName === 'Netscape' && /Trident/.test(navigator.userAgent)) { // IE 11
      return true;
    }
    return false;
  },
  countTruthy: function (values) {
    return values.reduce(function (sum, value) { return sum + (value ? 1 : 0); }, 0);
  },
  /**
   * Checks whether the browser is based on EdgeHTML (the pre-Chromium Edge engine) without using user-agent.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isEdgeHTML: function () {
    // Based on research in October 2020
    var w = window;
    var n = navigator;
    return (this.countTruthy(['msWriteProfilerMark' in w, 'MSStream' in w, 'msLaunchUri' in n, 'msSaveBlob' in n]) >= 3 &&
      !this.isTrident());
  },
  /*
   * Functions to help with features that vary through browsers
   */
  /**
   * Checks whether the browser is based on Trident (the Internet Explorer engine) without using user-agent.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isTrident: function () {
    var w = window;
    var n = navigator;
    // The properties are checked to be in IE 10, IE 11 and not to be in other browsers in October 2020
    return (this.countTruthy([
      'MSCSSMatrix' in w,
      'msSetImmediate' in w,
      'msIndexedDB' in w,
      'msMaxTouchPoints' in n,
      'msPointerEnabled' in n,
    ]) >= 4);
  },
  /**
   * Checks whether the browser is based on Chromium without using user-agent.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isChromium: function () {
    // Based on research in October 2020. Tested to detect Chromium 42-86.
    var w = window;
    var n = navigator;
    return (this.countTruthy([
      'webkitPersistentStorage' in n,
      'webkitTemporaryStorage' in n,
      n.vendor && n.vendor.indexOf('Google') === 0,
      'webkitResolveLocalFileSystemURL' in w,
      'BatteryManager' in w,
      'webkitMediaStream' in w,
      'webkitSpeechGrammar' in w,
    ]) >= 5);
  },
  /**
   * Checks whether the browser is based on Gecko (Firefox engine) without using user-agent.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isGecko: function () {
    var _a, _b;
    var w = window;
    // Based on research in September 2020
    return (this.countTruthy([
      'buildID' in navigator,
      'MozAppearance' in ((_b = (_a = document.documentElement) === null || _a === void 0 ? void 0 : _a.style) !== null && _b !== void 0 ? _b : {}),
      'MediaRecorderErrorEvent' in w,
      'mozInnerScreenX' in w,
      'CSSMozDocumentRule' in w,
      'CanvasCaptureMediaStream' in w,
    ]) >= 4);
  },
  /**
   * Checks whether the browser is based on Chromium version ≥86 without using user-agent.
   * It doesn't check that the browser is based on Chromium, there is a separate function for this.
   */
  isChromium86OrNewer: function () {
    // Checked in Chrome 85 vs Chrome 86 both on desktop and Android
    var w = window;
    return (this.countTruthy([
      !('MediaSettingsRange' in w),
      'RTCEncodedAudioFrame' in w,
      '' + w.Intl === '[object Intl]',
      '' + w.Reflect === '[object Reflect]',
    ]) >= 3);
  },
  /**
   * Checks whether the browser is based on WebKit version ≥606 (Safari ≥12) without using user-agent.
   * It doesn't check that the browser is based on WebKit, there is a separate function for this.
   *
   * @link https://en.wikipedia.org/wiki/Safari_version_history#Release_history Safari-WebKit versions map
   */
  isWebKit606OrNewer: function () {
    // Checked in Safari 9–14
    var w = window;
    return (this.countTruthy([
      'DOMRectList' in w,
      'RTCPeerConnectionIceEvent' in w,
      'SVGGeometryElement' in w,
      'ontransitioncancel' in w,
    ]) >= 3);
  },
  /**
   * Checks whether the device is an iPad.
   * It doesn't check that the engine is WebKit and that the WebKit isn't desktop.
   */
  isIPad: function () {
    // Checked on:
    // Safari on iPadOS (both mobile and desktop modes): 8, 11, 12, 13, 14
    // Chrome on iPadOS (both mobile and desktop modes): 11, 12, 13, 14
    // Safari on iOS (both mobile and desktop modes): 9, 10, 11, 12, 13, 14
    // Chrome on iOS (both mobile and desktop modes): 9, 10, 11, 12, 13, 14
    // Before iOS 13. Safari tampers the value in "request desktop site" mode since iOS 13.
    if (navigator.platform === 'iPad') {
      return true;
    }
    var s = screen;
    var screenRatio = s.width / s.height;
    return (this.countTruthy([
      'MediaSource' in window,
      !!Element.prototype.webkitRequestFullscreen,
      screenRatio > 2 / 3 && screenRatio < 3 / 2,
    ]) >= 2);
  },
  /**
   * Checks whether the WebKit browser is a desktop Safari.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isDesktopSafari: function () {
    var w = window;
    return (this.countTruthy([
      'safari' in w,
      !('DeviceMotionEvent' in w),
      !('ongestureend' in w),
      !('standalone' in navigator),
    ]) >= 3);
  },
  isWebKit: function () {
    // Based on research in September 2020
    var w = window;
    var n = navigator;
    return (this.countTruthy([
      'ApplePayError' in w,
      'CSSPrimitiveValue' in w,
      'Counter' in w,
      n.vendor && n.vendor.indexOf('Apple') === 0,
      'getStorageUpdates' in n,
      'WebKitMediaKeys' in w,
    ]) >= 4);
  },
  /**
   * Checks whether the device runs on Android without using user-agent.
   *
   * Warning for package users:
   * This function is out of Semantic Versioning, i.e. can change unexpectedly. Usage is at your own risk.
   */
  isAndroid: function () {
    var isItChromium = this.isChromium();
    var isItGecko = this.isGecko();
    // Only 2 browser engines are presented on Android.
    // Actually, there is also Android 4.1 browser, but it's not worth detecting it at the moment.
    if (!isItChromium && !isItGecko) {
      return false;
    }
    var w = window;
    // Chrome removes all words "Android" from `navigator` when desktop version is requested
    // Firefox keeps "Android" in `navigator.appVersion` when desktop version is requested
    return (this.countTruthy([
      'onorientationchange' in w,
      'orientation' in w,
      isItChromium && 'SharedWorker' in w,
      isItGecko && /android/i.test(navigator.appVersion),
    ]) >= 2);
  },
  /*
   * 色域范围
   */
  getColorGamut: function () {
    const colorList = [];
    try {
      for (const gamut of ['rec2020', 'p3', 'srgb']) {
        if (matchMedia(`(color-gamut: ${gamut})`).matches) {
          colorList.push(gamut)
        }
      }
    } catch (e) { }

    return colorList
  },
  /*
   * 是否开启颜色强制转换
   */
  getColorsForced: function () {
    function doesMatch(value) {
      return matchMedia(`(forced-colors: ${value})`).matches
    }
    try {
      if (doesMatch('active')) {
        return 1
      }
      if (doesMatch('none')) {
        return 2
      }
    } catch (e) { }

    return 0
  },
  /*
   * 是否开启HDR
   */
  getIsHDR: function () {
    function doesMatch(value) {
      return matchMedia(`(dynamic-range: ${value})`).matches
    }
    try {
      if (doesMatch('high')) {
        return 1
      }
      if (doesMatch('standard')) {
        return 2
      }
    } catch (e) { }

    return 0
  },
  /*
   * 动画效果减弱功能
   * https://developer.mozilla.org/zh-CN/docs/Web/CSS/@media/prefers-reduced-motion
   */
  getIsMotionReduced: function () {
    function doesMatch(value) {
      return matchMedia(`(prefers-reduced-motion: ${value})`).matches
    }
    try {
      if (doesMatch('reduce')) {
        return 1
      }
      if (doesMatch('no-preference')) {
        return 2
      }
    } catch (e) { }

    return 0
  },
  getMathFingerprint: function () {
    const M = Math // To reduce the minified code size
    const fallbackFn = () => 0

    // Native operations
    const acos = M.acos || fallbackFn
    const acosh = M.acosh || fallbackFn
    const asin = M.asin || fallbackFn
    const asinh = M.asinh || fallbackFn
    const atanh = M.atanh || fallbackFn
    const atan = M.atan || fallbackFn
    const sin = M.sin || fallbackFn
    const sinh = M.sinh || fallbackFn
    const cos = M.cos || fallbackFn
    const cosh = M.cosh || fallbackFn
    const tan = M.tan || fallbackFn
    const tanh = M.tanh || fallbackFn
    const exp = M.exp || fallbackFn
    const expm1 = M.expm1 || fallbackFn
    const log1p = M.log1p || fallbackFn

    // Operation polyfills
    const powPI = (value) => M.pow(M.PI, value)
    const acoshPf = (value) => M.log(value + M.sqrt(value * value - 1))
    const asinhPf = (value) => M.log(value + M.sqrt(value * value + 1))
    const atanhPf = (value) => M.log((1 + value) / (1 - value)) / 2
    const sinhPf = (value) => M.exp(value) - 1 / M.exp(value) / 2
    const coshPf = (value) => (M.exp(value) + 1 / M.exp(value)) / 2
    const expm1Pf = (value) => M.exp(value) - 1
    const tanhPf = (value) => (M.exp(2 * value) - 1) / (M.exp(2 * value) + 1)
    const log1pPf = (value) => M.log(1 + value)
    try {
      return {
        '00': '' + acos(0.123124234234234242),
        '01': '' + acosh(1e308),
        '02': '' + acoshPf(1e154), // 1e308 will not work for polyfill
        '03': '' + asin(0.123124234234234242),
        '04': '' + asinh(1),
        '05': '' + asinhPf(1),
        '06': '' + atanh(0.5),
        '07': '' + atanhPf(0.5),
        '08': '' + atan(0.5),
        '09': '' + cos(10.000000000123),
        '10': '' + cosh(1),
        '11': '' + coshPf(1),
        '12': '' + exp(1),
        '13': '' + expm1(1),
        '14': '' + expm1Pf(1),
        '15': '' + log1p(10),
        '16': '' + log1pPf(10),
        '17': '' + powPI(-100),
        '18': '' + sin(-1e300),
        '19': '' + sinh(1),
        '20': '' + sinhPf(1),
        '21': '' + tan(-1e300),
        '22': '' + tanh(1),
        '23': '' + tanhPf(1),
      }
    } catch (e) {
      console.error(e)
    }
  },
  getRegularPlugins: function () {
    var plugins = [];
    if (navigator.plugins) {
      // plugins isn't defined in Node envs.
      for (var i = 0, l = navigator.plugins.length; i < l; i++) {
        if (navigator.plugins[i]) {
          plugins.push([navigator.plugins[i]['name'], navigator.plugins[i]['filename'], navigator.plugins[i]['description']]);
        }
      }
    }
    return plugins;
  },
  getIEPlugins: function () {
    var result = [];
    if ((Object.getOwnPropertyDescriptor && Object.getOwnPropertyDescriptor(window, 'ActiveXObject')) || ('ActiveXObject' in window)) {
      var names = [
        'AcroPDF.PDF', // Adobe PDF reader 7+
        'Adodb.Stream',
        'AgControl.AgControl', // Silverlight
        'DevalVRXCtrl.DevalVRXCtrl.1',
        'MacromediaFlashPaper.MacromediaFlashPaper',
        'Msxml2.DOMDocument',
        'Msxml2.XMLHTTP',
        'PDF.PdfCtrl', // Adobe PDF reader 6 and earlier, brrr
        'QuickTime.QuickTime', // QuickTime
        'QuickTimeCheckObject.QuickTimeCheck.1',
        'RealPlayer',
        'RealPlayer.RealPlayer(tm) ActiveX Control (32-bit)',
        'RealVideo.RealVideo(tm) ActiveX Control (32-bit)',
        'Scripting.Dictionary',
        'SWCtl.SWCtl', // ShockWave player
        'Shell.UIHelper',
        'ShockwaveFlash.ShockwaveFlash', // flash plugin
        'Skype.Detection',
        'TDCCtl.TDCCtl',
        'WMPlayer.OCX', // Windows media player
        'rmocx.RealPlayer G2 Control',
        'rmocx.RealPlayer G2 Control.1',
        'TamperIE' // 作弊插件
      ];
      // starting to detect plugins in IE
      for (var i = 0; i < names.length; i++) {
        try {
          // eslint-disable-next-line no-new
          new window.ActiveXObject(names[i]);
          result.push(names[i]);
        } catch (e) {
        }
      }
    }
    if (navigator.plugins) {
      result = result.concat(this.getRegularPlugins());
    }
    return result;
  },
  getTouchSupport: function () {
    var maxTouchPoints = 0;
    var touchEvent = false;
    if (typeof navigator.maxTouchPoints !== 'undefined') {
      maxTouchPoints = navigator.maxTouchPoints;
    } else if (typeof navigator.msMaxTouchPoints !== 'undefined') {
      maxTouchPoints = navigator.msMaxTouchPoints;
    }
    try {
      document.createEvent('TouchEvent');
      touchEvent = true;
    } catch (_) { /* squelch */
    }
    var touchStart = 'ontouchstart' in window;
    return [maxTouchPoints, touchEvent, touchStart];
  },
  hasSessionStorage: function () {
    try {
      return (!!window.sessionStorage) ? 1 : 0
    } catch (e) {
      return 1 // SecurityError when referencing it means it exists
    }
  },
  hasLocalStorage: function () {
    try {
      return (!!window.localStorage) ? 1 : 0
    } catch (e) {
      return 1 // SecurityError when referencing it means it exists
    }
  },
  hasJavaEnable: function () {
    try {
      return navigator.javaEnabled() ? 1 : 0
    } catch (e) {
      return 0 // SecurityError when referencing it means it exists
    }
  },
  hasIndexedDB: function () {
    try {
      return (!!window.indexedDB) ? 1 : 0
    } catch (e) {
      return 1 // SecurityError when referencing it means it exists
    }
  },
  getAdBlock: function () {
    var ads = document.createElement('div')
    ads.innerHTML = '&nbsp;'
    ads.className = 'adsbox'
    var result = 0
    try {
      // body may not exist, that's why we need try/catch
      document.body.appendChild(ads)
      result = document.getElementsByClassName('adsbox')[0].offsetHeight === 0 ? 1 : 0
      document.body.removeChild(ads)
    } catch (e) {
      result = 0
    }
    return result
  },
  getHasLiedLanguages: function () {
    // We check if navigator.language is equal to the first language of navigator.languages
    if (typeof navigator.languages !== 'undefined') {
      try {
        var firstLanguages = navigator.languages[0].substr(0, 2)
        if (firstLanguages !== navigator.language.substr(0, 2)) {
          return 1
        }
      } catch (err) {
        return 1
      }
    }
    return 0
  },
  getHasLiedResolution: function () {
    if (window.screen.width < window.screen.availWidth) {
      return 1
    }
    if (window.screen.height < window.screen.availHeight) {
      return 1
    }
    return 0
  },
  getHasLiedOs: function () {
    var userAgent = navigator.userAgent.toLowerCase()
    var oscpu = navigator.oscpu
    var platform = navigator.platform.toLowerCase()
    var os
    // We extract the OS from the user agent (respect the order of the if else if statement)
    if (userAgent.indexOf('windows phone') >= 0) {
      os = 'Windows Phone'
    } else if (userAgent.indexOf('win') >= 0) {
      os = 'Windows'
    } else if (userAgent.indexOf('android') >= 0) {
      os = 'Android'
    } else if (userAgent.indexOf('linux') >= 0) {
      os = 'Linux'
    } else if (userAgent.indexOf('iphone') >= 0 || userAgent.indexOf('ipad') >= 0) {
      os = 'iOS'
    } else if (userAgent.indexOf('mac') >= 0) {
      os = 'Mac'
    } else {
      os = 'Other'
    }
    // We detect if the person uses a mobile device
    var mobileDevice
    if (('ontouchstart' in window) ||
      (navigator.maxTouchPoints > 0) ||
      (navigator.msMaxTouchPoints > 0)) {
      mobileDevice = true
    } else {
      mobileDevice = false
    }

    if (mobileDevice && os !== 'Windows Phone' && os !== 'Android' && os !== 'iOS' && os !== 'Other') {
      return 1
    }

    // We compare oscpu with the OS extracted from the UA
    if (typeof oscpu !== 'undefined') {
      oscpu = oscpu.toLowerCase()
      if (oscpu.indexOf('win') >= 0 && os !== 'Windows' && os !== 'Windows Phone') {
        return 1
      } else if (oscpu.indexOf('linux') >= 0 && os !== 'Linux' && os !== 'Android') {
        return 1
      } else if (oscpu.indexOf('mac') >= 0 && os !== 'Mac' && os !== 'iOS') {
        return 1
      } else if ((oscpu.indexOf('win') === -1 && oscpu.indexOf('linux') === -1 && oscpu.indexOf('mac') === -1) !== (os === 'Other')) {
        return 1
      }
    }

    // We compare platform with the OS extracted from the UA
    if (platform.indexOf('win') >= 0 && os !== 'Windows' && os !== 'Windows Phone') {
      return 1
    } else if ((platform.indexOf('linux') >= 0 || platform.indexOf('android') >= 0 || platform.indexOf('pike') >= 0) && os !== 'Linux' && os !== 'Android') {
      return 1
    } else if ((platform.indexOf('mac') >= 0 || platform.indexOf('ipad') >= 0 || platform.indexOf('ipod') >= 0 || platform.indexOf('iphone') >= 0) && os !== 'Mac' && os !== 'iOS') {
      return 1
    } else if ((platform.indexOf('win') === -1 && platform.indexOf('linux') === -1 && platform.indexOf('mac') === -1) !== (os === 'Other')) {
      return 1
    }

    if (typeof navigator.plugins === 'undefined' && os !== 'Windows' && os !== 'Windows Phone') {
      // We are are in the case where the person uses ie, therefore we can infer that it's windows
      return 1
    }

    return 0
  },
  getHasLiedBrowser: function () {
    var userAgent = navigator.userAgent.toLowerCase()
    var productSub = navigator.productSub

    // we extract the browser from the user agent (respect the order of the tests)
    var browser
    if (userAgent.indexOf('firefox') >= 0) {
      browser = 'Firefox'
    } else if (userAgent.indexOf('opera') >= 0 || userAgent.indexOf('opr') >= 0) {
      browser = 'Opera'
    } else if (userAgent.indexOf('chrome') >= 0) {
      browser = 'Chrome'
    } else if (userAgent.indexOf('safari') >= 0) {
      browser = 'Safari'
    } else if (userAgent.indexOf('trident') >= 0) {
      browser = 'Internet Explorer'
    } else {
      browser = 'Other'
    }

    if ((browser === 'Chrome' || browser === 'Safari' || browser === 'Opera') && productSub !== '20030107') {
      return 1
    }

    // eslint-disable-next-line no-eval
    var tempRes = eval.toString().length
    if (tempRes === 37 && browser !== 'Safari' && browser !== 'Firefox' && browser !== 'Other') {
      return 1
    } else if (tempRes === 39 && browser !== 'Internet Explorer' && browser !== 'Other') {
      return 1
    } else if (tempRes === 33 && browser !== 'Chrome' && browser !== 'Opera' && browser !== 'Other') {
      return 1
    }

    // We create an error to see how it is handled
    var errFirefox
    try {
      // eslint-disable-next-line no-throw-literal
      throw 'a'
    } catch (err) {
      try {
        err.toSource()
        errFirefox = true
      } catch (errOfErr) {
        errFirefox = false
      }
    }
    if (errFirefox && browser !== 'Firefox' && browser !== 'Other') {
      return 1
    }
    return 0
  },
  /**
   * Checks for browser-specific (not engine specific) global variables to tell browsers with the same engine apart.
   * Only somewhat popular browsers are considered.
   */
  getVendorFlavors: function () {
    var flavors = [];
    for (var _i = 0, _a = [
      // Blink and some browsers on iOS
      'chrome',
      // Safari on macOS
      'safari',
      // Chrome on iOS (checked in 85 on 13 and 87 on 14)
      '__crWeb',
      '__gCrWeb',
      // Yandex Browser on iOS, macOS and Android (checked in 21.2 on iOS 14, macOS and Android)
      'yandex',
      // Yandex Browser on iOS (checked in 21.2 on 14)
      '__yb',
      '__ybro',
      // Firefox on iOS (checked in 32 on 14)
      '__firefox__',
      // Edge on iOS (checked in 46 on 14)
      '__edgeTrackingPreventionStatistics',
      'webkit',
      // Opera Touch on iOS (checked in 2.6 on 14)
      'oprt',
      // Samsung Internet on Android (checked in 11.1)
      'samsungAr',
      // UC Browser on Android (checked in 12.10 and 13.0)
      'ucweb',
      'UCShellJava',
      // Puffin on Android (checked in 9.0)
      'puffinDevice',
    ]; _i < _a.length; _i++) {
      var key = _a[_i];
      var value = window[key];
      if (value && typeof value === 'object') {
        flavors.push(key);
      }
    }
    return flavors.sort();
  },
  isCanvasSupported: function () {
    if (this.c && this.d) {
      return true
    }
    try {
      let elem = document.createElement('canvas');
      elem.width = 1;
      elem.height = 1;
      this.c = elem;
      this.d = elem.getContext('2d');
      if (this.d) {
        return true
      } else {
        this.c = null
        this.d = null
        return false
      }
    } catch (e) {
      return false
    }
    // try{
    //   elem.remove()
    // } catch (e) {
    //   elem = null
    // }
  },
  isWebGlSupported: function () {
    // code taken from Modernizr
    if (!this.isCanvasSupported()) {
      return false
    }

    const gl = this.getWebglCanvas();
    const supGl = !!gl
    try {
      gl.getExtension('WEBGL_lose_context').loseContext();
    } catch (e) {

    }
    return !!window.WebGLRenderingContext && supGl
  },
  getCanvasTextFp: function () {
    // Very simple now, need to make it more complex (geo shapes etc)
    if (!this.isCanvasSupported()) {
      return ''
    }
    this.c.width = 240;
    this.c.height = 60;

    this.d.textBaseline = 'alphabetic'
    this.d.fillStyle = '#f60'
    this.d.fillRect(100, 1, 62, 20)

    this.d.fillStyle = '#069'
    // It's important to use explicit built-in fonts in order to exclude the affect of font preferences
    // (there is a separate entropy source for them).
    this.d.font = '11pt "Times New Roman"'
    // The choice of emojis has a gigantic impact on rendering performance (especially in FF).
    // Some newer emojis cause it to slow down 50-200 times.
    // There must be no text to the right of the emoji, see https://github.com/fingerprintjs/fingerprintjs/issues/574
    // A bare emoji shouldn't be used because the canvas will change depending on the script encoding:
    // https://github.com/fingerprintjs/fingerprintjs/issues/66
    // Escape sequence shouldn't be used too because Terser will turn it into a bare unicode.
    const printedText = `Cwm fjordbank glyphs vext quiz ${String.fromCharCode(55357, 56835) /* 😃 */}`
    this.d.fillText(printedText, 2, 15)
    this.d.fillStyle = 'rgba(102, 204, 0, 0.2)'
    this.d.font = '18pt Arial'
    this.d.fillText(printedText, 4, 45)

    return this.c.toDataURL();
  },
  getCanvasFp: function () {
    // Very simple now, need to make it more complex (geo shapes etc)
    if (!this.isCanvasSupported()) {
      return ''
    }
    // let canvas = document.createElement('canvas');
    this.c.width = 122;
    this.c.height = 110;
    // canvas.style.display = 'inline';
    // let ctx = canvas.getContext('2d');
    // Canvas blending
    // https://web.archive.org/web/20170826194121/http://blogs.adobe.com/webplatform/2013/01/28/blending-features-in-canvas/
    // http://jsfiddle.net/NDYV8/16/
    // this.d.globalCompositeOperation = 'multiply'
    // for (const [color, x, y] of [
    //   ['#f2f', 40, 40],
    //   ['#2ff', 80, 40],
    //   ['#ff2', 60, 80],
    // ]) {
    //   this.d.fillStyle = color
    //   this.d.beginPath()
    //   this.d.arc(x, y, 40, 0, Math.PI * 2, true)
    //   this.d.closePath()
    //   this.d.fill()
    // }
    //
    //
    // // detect browser support of canvas winding
    // // http://blogs.adobe.com/webplatform/2013/01/30/winding-rules-in-canvas/
    // // https://github.com/Modernizr/Modernizr/blob/master/feature-detects/canvas/winding.js
    // this.d.rect(0, 0, 10, 10);
    // this.d.rect(2, 2, 6, 6);
    //
    // this.d.textBaseline = 'alphabetic';
    // this.d.fillStyle = '#f60';
    // this.d.fillRect(125, 1, 62, 20);
    // this.d.fillStyle = '#069';
    // // https://github.com/Valve/fingerprintjs2/issues/66
    // this.d.font = '11pt Arial';
    // const plantText = `Cwm fjordbank glyphs vext quiz, ${String.fromCharCode(55357, 56835) /* 😃 */}`
    // this.d.fillText(plantText, 2, 15);
    // this.d.fillStyle = 'rgba(102, 204, 0, 0.2)';
    // this.d.font = '18pt Arial';
    // this.d.fillText(plantText, 4, 45);
    //
    //
    // // Canvas winding
    // // http://blogs.adobe.com/webplatform/2013/01/30/winding-rules-in-canvas/
    // // http://jsfiddle.net/NDYV8/19/
    // this.d.fillStyle = '#f9c'
    // this.d.arc(60, 60, 60, 0, Math.PI * 2, true)
    // this.d.arc(60, 60, 20, 0, Math.PI * 2, true)
    // this.d.fill('evenodd')
    this.d.globalCompositeOperation = 'multiply'
    for (const [color, x, y] of [
      ['#f2f', 40, 40],
      ['#2ff', 80, 40],
      ['#ff2', 60, 80],
    ]) {
      this.d.fillStyle = color
      this.d.beginPath()
      this.d.arc(x, y, 40, 0, Math.PI * 2, true)
      this.d.closePath()
      this.d.fill()
    }

    // Canvas winding
    // http://blogs.adobe.com/webplatform/2013/01/30/winding-rules-in-canvas/
    // http://jsfiddle.net/NDYV8/19/
    this.d.fillStyle = '#f9c'
    this.d.arc(60, 60, 60, 0, Math.PI * 2, true)
    this.d.arc(60, 60, 20, 0, Math.PI * 2, true)
    this.d.fill('evenodd')

    return this.c.toDataURL();
  },
  getCanvasFpCrc: function(){
    return CRC.crc32(this.getCanvasFp().replace('data:image/png;base64,', ''));
  },
  getCanvas3dHash: function () {
    // This function is based on this article: https://bl.ocks.org/camargo/649e5903c4584a21a568972d4a2c16d3
    // The 'mat4' object is created from code in the gl-matrix library: https://github.com/toji/gl-matrix
    var gl, program, canvas;

    // The non-minified versions of these shaders are available in
    // WebSite/partials/fragment-shader.glsl and
    // WebSite/partials/vertex-shader.glsl
    var VERTEX_SHADER = 'attribute vec3 c,d; uniform vec4 e; uniform vec3 f,g;uniform mat4 h,i;varying vec3 j;void main(){vec3 a=normalize(d);vec4 b=h*vec4(c,1.);vec3 k=normalize(vec3(e-b));j=g*f*max(dot(k,a),0.),gl_Position=i*vec4(c,1.);}';
    var FRAGMENT_SHADER = '#ifdef GL_ES\n' +
      'precision mediump float;\n' +
      '#endif\n' +
      'varying vec3 j;void main(){gl_FragColor = vec4(j, 1.0);}';

    // This object uses code from the gl-matrix library: https://github.com/toji/gl-matrix
    var mat4 = {
      create: function () {
        var result = new Array(16);
        for (var i = 0; i < 16; i++) {
          result[i] = (i % 5 == 0 ? 1 : 0);
        }
        return result;
      },
      perspective: function (out, fovy, aspect, near, far) {
        var f = 1.0 / Math.tan(fovy / 2), nf;
        out[0] = f / aspect;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
        out[4] = 0;
        out[5] = f;
        out[6] = 0;
        out[7] = 0;
        out[8] = 0;
        out[9] = 0;
        out[11] = -1;
        out[12] = 0;
        out[13] = 0;
        out[15] = 0;
        if (far != null && far !== Infinity) {
          nf = 1 / (near - far);
          out[10] = (far + near) * nf;
          out[14] = (2 * far * near) * nf;
        } else {
          out[10] = -1;
          out[14] = -2 * near;
        }
        return out;
      },
      lookAt: function (out, eye, center, up) {
        var x0, x1, x2, y0, y1, y2, z0, z1, z2, len;
        var eyex = eye[0];
        var eyey = eye[1];
        var eyez = eye[2];
        var upx = up[0];
        var upy = up[1];
        var upz = up[2];
        var centerx = center[0];
        var centery = center[1];
        var centerz = center[2];

        if (Math.abs(eyex - centerx) < 0.000001 &&
          Math.abs(eyey - centery) < 0.000001 &&
          Math.abs(eyez - centerz) < 0.000001) {
          return mat4.identity(out);
        }

        z0 = eyex - centerx;
        z1 = eyey - centery;
        z2 = eyez - centerz;

        len = 1 / Math.hypot(z0, z1, z2);
        z0 *= len;
        z1 *= len;
        z2 *= len;

        x0 = upy * z2 - upz * z1;
        x1 = upz * z0 - upx * z2;
        x2 = upx * z1 - upy * z0;
        len = Math.hypot(x0, x1, x2);
        if (!len) {
          x0 = 0;
          x1 = 0;
          x2 = 0;
        } else {
          len = 1 / len;
          x0 *= len;
          x1 *= len;
          x2 *= len;
        }

        y0 = z1 * x2 - z2 * x1;
        y1 = z2 * x0 - z0 * x2;
        y2 = z0 * x1 - z1 * x0;

        len = Math.hypot(y0, y1, y2);
        if (!len) {
          y0 = 0;
          y1 = 0;
          y2 = 0;
        } else {
          len = 1 / len;
          y0 *= len;
          y1 *= len;
          y2 *= len;
        }

        out[0] = x0;
        out[1] = y0;
        out[2] = z0;
        out[3] = 0;
        out[4] = x1;
        out[5] = y1;
        out[6] = z1;
        out[7] = 0;
        out[8] = x2;
        out[9] = y2;
        out[10] = z2;
        out[11] = 0;
        out[12] = -(x0 * eyex + x1 * eyey + x2 * eyez);
        out[13] = -(y0 * eyex + y1 * eyey + y2 * eyez);
        out[14] = -(z0 * eyex + z1 * eyey + z2 * eyez);
        out[15] = 1;

        return out;
      },
      multiply: function (out, a, b) {
        var a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3];
        var a10 = a[4], a11 = a[5], a12 = a[6], a13 = a[7];
        var a20 = a[8], a21 = a[9], a22 = a[10], a23 = a[11];
        var a30 = a[12], a31 = a[13], a32 = a[14], a33 = a[15];

        var b0 = b[0], b1 = b[1], b2 = b[2], b3 = b[3];
        out[0] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
        out[1] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
        out[2] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
        out[3] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

        b0 = b[4]; b1 = b[5]; b2 = b[6]; b3 = b[7];
        out[4] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
        out[5] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
        out[6] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
        out[7] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

        b0 = b[8]; b1 = b[9]; b2 = b[10]; b3 = b[11];
        out[8] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
        out[9] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
        out[10] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
        out[11] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;

        b0 = b[12]; b1 = b[13]; b2 = b[14]; b3 = b[15];
        out[12] = b0 * a00 + b1 * a10 + b2 * a20 + b3 * a30;
        out[13] = b0 * a01 + b1 * a11 + b2 * a21 + b3 * a31;
        out[14] = b0 * a02 + b1 * a12 + b2 * a22 + b3 * a32;
        out[15] = b0 * a03 + b1 * a13 + b2 * a23 + b3 * a33;
        return out;
      },
      identity: function (out) {
        out[0] = 1;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
        out[4] = 0;
        out[5] = 1;
        out[6] = 0;
        out[7] = 0;
        out[8] = 0;
        out[9] = 0;
        out[10] = 1;
        out[11] = 0;
        out[12] = 0;
        out[13] = 0;
        out[14] = 0;
        out[15] = 1;
        return out;
      }
    };

    function initVertexBuffers(gl) {
      var latitudeBands = 50;
      var longitudeBands = 50;
      var radius = 2;

      var vertexPositionData = [];
      var normalData = [];
      var textureCoordData = [];
      var indexData = [];

      var latNumber, longNumber;

      // Calculate sphere vertex positions, normals, and texture coordinates.
      for (latNumber = 0; latNumber <= latitudeBands; ++latNumber) {
        var theta = latNumber * Math.PI / latitudeBands;
        var sinTheta = Math.sin(theta);
        var cosTheta = Math.cos(theta);

        for (longNumber = 0; longNumber <= longitudeBands; ++longNumber) {
          var phi = longNumber * 2 * Math.PI / longitudeBands;
          var sinPhi = Math.sin(phi);
          var cosPhi = Math.cos(phi);

          var x = cosPhi * sinTheta;
          var y = cosTheta;
          var z = sinPhi * sinTheta;

          var u = 1 - (longNumber / longitudeBands);
          var v = 1 - (latNumber / latitudeBands);

          vertexPositionData.push(radius * x);
          vertexPositionData.push(radius * y);
          vertexPositionData.push(radius * z);

          normalData.push(x);
          normalData.push(y);
          normalData.push(z);

          textureCoordData.push(u);
          textureCoordData.push(v);
        }
      }

      // Calculate sphere indices.
      for (latNumber = 0; latNumber < latitudeBands; ++latNumber) {
        for (longNumber = 0; longNumber < longitudeBands; ++longNumber) {
          var first = (latNumber * (longitudeBands + 1)) + longNumber;
          var second = first + longitudeBands + 1;

          indexData.push(first);
          indexData.push(second);
          indexData.push(first + 1);

          indexData.push(second);
          indexData.push(second + 1);
          indexData.push(first + 1);
        }
      }

      vertexPositionData = new Float32Array(vertexPositionData);
      normalData = new Float32Array(normalData);
      textureCoordData = new Float32Array(textureCoordData);
      indexData = new Uint16Array(indexData);

      // Create buffer objects.
      var vertexPositionBuffer = gl.createBuffer();
      var vertexNormalBuffer = gl.createBuffer();
      var indexBuffer = gl.createBuffer();

      // Write the vertex positions to their buffer object.
      gl.bindBuffer(gl.ARRAY_BUFFER, vertexPositionBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, vertexPositionData, gl.STATIC_DRAW);

      // Assign position coords to attrib and enable it.
      var VertexPosition = gl.getAttribLocation(program, 'c');
      gl.vertexAttribPointer(VertexPosition, 3, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(VertexPosition);

      // Write the normals to their buffer object.
      gl.bindBuffer(gl.ARRAY_BUFFER, vertexNormalBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, normalData, gl.STATIC_DRAW);

      // Assign normal to attrib and enable it.
      var VertexNormal = gl.getAttribLocation(program, 'd');
      gl.vertexAttribPointer(VertexNormal, 3, gl.FLOAT, false, 0, 0);
      gl.enableVertexAttribArray(VertexNormal);

      // Pass index buffer data to element array buffer.
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
      gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indexData, gl.STATIC_DRAW);

      return indexData.length;
    }

    function generate() {
      if (!(gl === getRenderingContext()))
        return;

      var vertexShader = gl.createShader(gl.VERTEX_SHADER);
      gl.shaderSource(vertexShader, VERTEX_SHADER);
      gl.compileShader(vertexShader);
      var fragmentShader = gl.createShader(gl.FRAGMENT_SHADER);
      gl.shaderSource(fragmentShader, FRAGMENT_SHADER);
      gl.compileShader(fragmentShader);
      program = gl.createProgram();
      gl.attachShader(program, vertexShader);
      gl.attachShader(program, fragmentShader);
      gl.linkProgram(program);
      gl.detachShader(program, vertexShader);
      gl.detachShader(program, fragmentShader);
      gl.deleteShader(vertexShader);
      gl.deleteShader(fragmentShader);
      gl.useProgram(program);

      // Init vertex buffers (position, color, and index data).
      var n = initVertexBuffers(gl);

      // Set up clear color and enable depth testing.
      gl.clearColor(0.0, 0.0, 0.0, 1.0);
      gl.enable(gl.DEPTH_TEST);

      // Create projection matrix.
      var projection = mat4.create();
      mat4.perspective(projection, Math.PI / 6, 1.0, 0.1, 100.0);

      // Create model-view matrix.
      var modelView = mat4.create();
      mat4.lookAt(modelView, [0.0, 0.0, 10.0], [0.0, 0.0, 0.0], [0.0, 1.0, 0.0]);

      // Multiply the projection matrix by the model-view matrix to create the mvpMatrix.
      var mvpMatrix = mat4.create();
      mat4.multiply(mvpMatrix, projection, modelView);

      // Pass the modelView matrix into the shader.
      var ModelViewMatrix = gl.getUniformLocation(program, 'h');
      gl.uniformMatrix4fv(ModelViewMatrix, false, modelView);

      // Pass the mvp matrix into the shader.
      var MVP = gl.getUniformLocation(program, 'i');
      gl.uniformMatrix4fv(MVP, false, mvpMatrix);

      // Pass the light position into the shader.
      var LightPosition = gl.getUniformLocation(program, 'e');
      gl.uniform4fv(LightPosition, [10.0, 10.0, 10.0, 1.0]);

      // Pass the material diffuse color into the shader.
      var Kd = gl.getUniformLocation(program, 'f');
      gl.uniform3fv(Kd, [0.9, 0.5, 0.3]);

      // Pass the light diffuse color into the shader.
      var Ld = gl.getUniformLocation(program, 'g');
      gl.uniform3fv(Ld, [1.0, 1.0, 1.0]);

      // Clear & draw.
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.drawElements(gl.TRIANGLES, n, gl.UNSIGNED_SHORT, 0);

      cleanup();
      return canvas.toDataURL();
    }

    function cleanup() {
      gl.useProgram(null);
      if (program) gl.deleteProgram(program);
      try {
        gl.getExtension('WEBGL_lose_context').loseContext();
      } catch (e) {

      }
    }

    function getRenderingContext() {
      canvas.width = 67;
      canvas.height = 67;
      var gl = canvas.getContext("webgl")
        || canvas.getContext("experimental-webgl");
      if (gl) {
        gl.viewport(0, 0, 67, 67);
        gl.clearColor(0.0, 0.0, 0.0, 1.0);
        gl.clear(gl.COLOR_BUFFER_BIT);
      }
      return gl;
    }
    function fnvHash(str) {
      var h = 0x811c9dc5;
      for (var i = 0; i < str.length; ++i) {
        h ^= str.charCodeAt(i);
        h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
      }
      return h >>> 0;
    }
    var imageHash = 0;

    canvas = document.createElement("canvas");
    if (canvas != null) {
      // Get the image data as a string.
      var imageData = generate();
      if (imageData) {
        // Hash the image data.
        imageHash = fnvHash(imageData);
      }
    }
    return imageHash;
  },
  getWebglFp: function () {
    if (!this.isWebGlSupported()) {
      return ''
    }
    let gl;
    var fa2s = function (fa) {
      gl.clearColor(0.0, 0.0, 0.0, 1.0);
      gl.enable(gl.DEPTH_TEST);
      gl.depthFunc(gl.LEQUAL);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      return '[' + fa[0] + ', ' + fa[1] + ']';
    };
    var maxAnisotropy = function (gl) {
      var ext = gl.getExtension('EXT_texture_filter_anisotropic') || gl.getExtension('WEBKIT_EXT_texture_filter_anisotropic') || gl.getExtension('MOZ_EXT_texture_filter_anisotropic');
      if (ext) {
        var anisotropy = gl.getParameter(ext.MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        if (anisotropy === 0) {
          anisotropy = 2;
        }
        return anisotropy;
      } else {
        return null;
      }
    };
    gl = this.getWebglCanvas();
    if (!gl) {
      return null;
    }
    // WebGL fingerprinting is a combination of techniques, found in MaxMind antifraud script & Augur fingerprinting.
    // First it draws a gradient object with shaders and convers the image to the Base64 string.
    // Then it enumerates all WebGL extensions & capabilities and appends them to the Base64 string, resulting in a huge WebGL string, potentially very unique on each device
    // Since iOS supports webgl starting from version 8.1 and 8.1 runs on several graphics chips, the results may be different across ios devices, but we need to verify it.
    var result = [];
    var vShaderTemplate = 'attribute vec2 attrVertex;varying vec2 varyinTexCoordinate;uniform vec2 uniformOffset;void main(){varyinTexCoordinate=attrVertex+uniformOffset;gl_Position=vec4(attrVertex,0,1);}';
    var fShaderTemplate = 'precision mediump float;varying vec2 varyinTexCoordinate;void main() {gl_FragColor=vec4(varyinTexCoordinate,0,1);}';
    var vertexPosBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexPosBuffer);
    var vertices = new Float32Array([-0.2, -0.9, 0, 0.4, -0.26, 0, 0, 0.732134444, 0]);
    gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
    vertexPosBuffer.itemSize = 3;
    vertexPosBuffer.numItems = 3;
    var program = gl.createProgram();
    var vshader = gl.createShader(gl.VERTEX_SHADER);
    gl.shaderSource(vshader, vShaderTemplate);
    gl.compileShader(vshader);
    var fshader = gl.createShader(gl.FRAGMENT_SHADER);
    gl.shaderSource(fshader, fShaderTemplate);
    gl.compileShader(fshader);
    gl.attachShader(program, vshader);
    gl.attachShader(program, fshader);
    gl.linkProgram(program);
    gl.useProgram(program);
    program.vertexPosAttrib = gl.getAttribLocation(program, 'attrVertex');
    program.offsetUniform = gl.getUniformLocation(program, 'uniformOffset');
    gl.enableVertexAttribArray(program.vertexPosArray);
    gl.vertexAttribPointer(program.vertexPosAttrib, vertexPosBuffer.itemSize, gl.FLOAT, !1, 0, 0);
    gl.uniform2f(program.offsetUniform, 1, 1);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, vertexPosBuffer.numItems);
    try {
      result.push(gl.canvas.toDataURL());
    } catch (e) {
      /* .toDataURL may be absent or broken (blocked by extension) */
    }
    result.push('extensions:' + (gl.getSupportedExtensions() || []).join(';'));
    result.push('webgl aliased line width range:' + fa2s(gl.getParameter(gl.ALIASED_LINE_WIDTH_RANGE)));
    result.push('webgl aliased point size range:' + fa2s(gl.getParameter(gl.ALIASED_POINT_SIZE_RANGE)));
    result.push('webgl alpha bits:' + gl.getParameter(gl.ALPHA_BITS));
    result.push('webgl antialiasing:' + (gl.getContextAttributes().antialias ? 'yes' : 'no'));
    result.push('webgl blue bits:' + gl.getParameter(gl.BLUE_BITS));
    result.push('webgl depth bits:' + gl.getParameter(gl.DEPTH_BITS));
    result.push('webgl green bits:' + gl.getParameter(gl.GREEN_BITS));
    result.push('webgl max anisotropy:' + maxAnisotropy(gl));
    result.push('webgl max combined texture image units:' + gl.getParameter(gl.MAX_COMBINED_TEXTURE_IMAGE_UNITS));
    result.push('webgl max cube map texture size:' + gl.getParameter(gl.MAX_CUBE_MAP_TEXTURE_SIZE));
    result.push('webgl max fragment uniform vectors:' + gl.getParameter(gl.MAX_FRAGMENT_UNIFORM_VECTORS));
    result.push('webgl max render buffer size:' + gl.getParameter(gl.MAX_RENDERBUFFER_SIZE));
    result.push('webgl max texture image units:' + gl.getParameter(gl.MAX_TEXTURE_IMAGE_UNITS));
    result.push('webgl max texture size:' + gl.getParameter(gl.MAX_TEXTURE_SIZE));
    result.push('webgl max varying vectors:' + gl.getParameter(gl.MAX_VARYING_VECTORS));
    result.push('webgl max vertex attribs:' + gl.getParameter(gl.MAX_VERTEX_ATTRIBS));
    result.push('webgl max vertex texture image units:' + gl.getParameter(gl.MAX_VERTEX_TEXTURE_IMAGE_UNITS));
    result.push('webgl max vertex uniform vectors:' + gl.getParameter(gl.MAX_VERTEX_UNIFORM_VECTORS));
    result.push('webgl max viewport dims:' + fa2s(gl.getParameter(gl.MAX_VIEWPORT_DIMS)));
    result.push('webgl red bits:' + gl.getParameter(gl.RED_BITS));
    result.push('webgl renderer:' + gl.getParameter(gl.RENDERER));
    result.push('webgl shading language version:' + gl.getParameter(gl.SHADING_LANGUAGE_VERSION));
    result.push('webgl stencil bits:' + gl.getParameter(gl.STENCIL_BITS));
    result.push('webgl vendor:' + gl.getParameter(gl.VENDOR));
    result.push('webgl version:' + gl.getParameter(gl.VERSION));

    try {
      // Add the unmasked vendor and unmasked renderer if the debug_renderer_info extension is available
      var extensionDebugRendererInfo = gl.getExtension('WEBGL_debug_renderer_info');
      if (extensionDebugRendererInfo) {
        result.push('webgl unmasked vendor:' + gl.getParameter(extensionDebugRendererInfo.UNMASKED_VENDOR_WEBGL));
        result.push('webgl unmasked renderer:' + gl.getParameter(extensionDebugRendererInfo.UNMASKED_RENDERER_WEBGL));
      }
    } catch (e) { /* squelch */
    }

    if (!gl.getShaderPrecisionFormat) {
      return result.join('~');
    }

    result.push('webgl vertex shader high float precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_FLOAT).precision);
    result.push('webgl vertex shader high float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_FLOAT).rangeMin);
    result.push('webgl vertex shader high float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_FLOAT).rangeMax);
    result.push('webgl vertex shader medium float precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_FLOAT).precision);
    result.push('webgl vertex shader medium float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_FLOAT).rangeMin);
    result.push('webgl vertex shader medium float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_FLOAT).rangeMax);
    result.push('webgl vertex shader low float precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_FLOAT).precision);
    result.push('webgl vertex shader low float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_FLOAT).rangeMin);
    result.push('webgl vertex shader low float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_FLOAT).rangeMax);
    result.push('webgl fragment shader high float precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT).precision);
    result.push('webgl fragment shader high float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT).rangeMin);
    result.push('webgl fragment shader high float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT).rangeMax);
    result.push('webgl fragment shader medium float precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_FLOAT).precision);
    result.push('webgl fragment shader medium float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_FLOAT).rangeMin);
    result.push('webgl fragment shader medium float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_FLOAT).rangeMax);
    result.push('webgl fragment shader low float precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_FLOAT).precision);
    result.push('webgl fragment shader low float precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_FLOAT).rangeMin);
    result.push('webgl fragment shader low float precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_FLOAT).rangeMax);
    result.push('webgl vertex shader high int precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_INT).precision);
    result.push('webgl vertex shader high int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_INT).rangeMin);
    result.push('webgl vertex shader high int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.HIGH_INT).rangeMax);
    result.push('webgl vertex shader medium int precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_INT).precision);
    result.push('webgl vertex shader medium int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_INT).rangeMin);
    result.push('webgl vertex shader medium int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.MEDIUM_INT).rangeMax);
    result.push('webgl vertex shader low int precision:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_INT).precision);
    result.push('webgl vertex shader low int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_INT).rangeMin);
    result.push('webgl vertex shader low int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.VERTEX_SHADER, gl.LOW_INT).rangeMax);
    result.push('webgl fragment shader high int precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_INT).precision);
    result.push('webgl fragment shader high int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_INT).rangeMin);
    result.push('webgl fragment shader high int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_INT).rangeMax);
    result.push('webgl fragment shader medium int precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_INT).precision);
    result.push('webgl fragment shader medium int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_INT).rangeMin);
    result.push('webgl fragment shader medium int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_INT).rangeMax);
    result.push('webgl fragment shader low int precision:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_INT).precision);
    result.push('webgl fragment shader low int precision rangeMin:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_INT).rangeMin);
    result.push('webgl fragment shader low int precision rangeMax:' + gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.LOW_INT).rangeMax);
    return result.join('~');
  },
  getWebglCanvas: function () {

    let canvas = document.createElement('canvas');
    let gl = null;
    try {
      gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
    } catch (e) { /* squelch */
    }
    if (!gl) {
      gl = null;
    }
    try {
      canvas.remove()
    } catch (e) {
      canvas = null
    }
    return gl;
  },
  getWebglRenderer: function () {
    /* This a subset of the WebGL fingerprint with a lot of entropy, while being reasonably browser-independent */
    try {
      const gl = this.getWebglCanvas();
      const extensionDebugRendererInfo = gl.getExtension('WEBGL_debug_renderer_info');
      const wglRender = gl.getParameter(extensionDebugRendererInfo.UNMASKED_RENDERER_WEBGL);
      try {
        gl.getExtension('WEBGL_lose_context').loseContext();
      } catch (e) {

      }
      return wglRender
    } catch (e) {
      return null;
    }
  },
  getWebglVendorAndRenderer: function () {
    /* This a subset of the WebGL fingerprint with a lot of entropy, while being reasonably browser-independent */
    try {
      let glContext = this.getWebglCanvas();
      const extensionDebugRendererInfo = glContext.getExtension('WEBGL_debug_renderer_info');
      const wglRender = glContext.getParameter(extensionDebugRendererInfo.UNMASKED_VENDOR_WEBGL) + '~' + glContext.getParameter(extensionDebugRendererInfo.UNMASKED_RENDERER_WEBGL);
      try {
        gl.getExtension('WEBGL_lose_context').loseContext();
      } catch (e) {

      }
      return wglRender
    } catch (e) {
      return null;
    }
  },
  clone: function (obj) {
    if (obj === null || typeof (obj) !== 'object' || 'isActiveClone' in obj)
      return obj;

    if (obj instanceof Date)
      var temp = new obj.constructor(); //or new Date(obj);
    else
      var temp = obj.constructor();

    for (var key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        obj['isActiveClone'] = null;
        temp[key] = this.clone(obj[key]);
        delete obj['isActiveClone'];
      }
    }

    return temp;
  },
  clean: function () {
    // console.log('清理图像')
    if (this.c || this.d) {
      try {
        this.c.remove()
        this.d = null
      } catch (e) {
        this.c = null
        this.d = null
      }
    }
  },
  getEmptyEvalLength: function () {
    return eval.toString().length;
  },
  getProductSub: function () {
    return navigator.productSub || '';
  },
  getErrorFF: function () {
    try {
      throw 'a';
    } catch (e) {
      try {
        e.toSource();
        return 1;
      } catch (e2) {
        return 0;
      }
    }
  },
  getFonts: function () {
    // get device Fonts
    // We use m or w because these two characters take up the maximum width.
    // And we use a LLi so that the same matching fonts can get separated.
    var testString = 'MMmmWwWLliI0O&1';
    // We test using 48px font size, we may use any size. I guess larger the better.
    var testSize = '48px';
    // A font will be compared against all the three default fonts.
    // And if it doesn't match all 3 then that font is not available.
    var baseFonts = ['monospace', 'sans-serif', 'serif'];
    var fontList = [
      // This is android-specific font from "Roboto" family
      'sans-serif-thin',
      'ARNO PRO',
      'Agency FB',
      'Arabic Typesetting',
      'Arial Unicode MS',
      'AvantGarde Bk BT',
      'BankGothic Md BT',
      'Batang',
      'Bitstream Vera Sans Mono',
      'Calibri',
      'Century',
      'Century Gothic',
      'Clarendon',
      'EUROSTILE',
      'Franklin Gothic',
      'Futura Bk BT',
      'Futura Md BT',
      'GOTHAM',
      'Gill Sans',
      'HELV',
      'Haettenschweiler',
      'Helvetica Neue',
      'Humanst521 BT',
      'Leelawadee',
      'Letter Gothic',
      'Levenim MT',
      'Lucida Bright',
      'Lucida Sans',
      'Menlo',
      'MS Mincho',
      'MS Outlook',
      'MS Reference Specialty',
      'MS UI Gothic',
      'MT Extra',
      'MYRIAD PRO',
      'Marlett',
      'Meiryo UI',
      'Microsoft Uighur',
      'Minion Pro',
      'Monotype Corsiva',
      'PMingLiU',
      'Pristina',
      'SCRIPTINA',
      'Segoe UI Light',
      'Serifa',
      'SimHei',
      'Small Fonts',
      'Staccato222 BT',
      'TRAJAN PRO',
      'Univers CE 55 Medium',
      'Vrinda',
      'ZWAdobeF',
    ];
    var fontSpanStyle = {
      // CSS font reset to reset external styles
      fontStyle: 'normal',
      fontWeight: 'normal',
      letterSpacing: 'normal',
      lineBreak: 'auto',
      lineHeight: 'normal',
      textTransform: 'none',
      textAlign: 'left',
      textDecoration: 'none',
      textShadow: 'none',
      whiteSpace: 'normal',
      wordBreak: 'normal',
      wordSpacing: 'normal',
      // We need this css as in some weird browser this span elements shows up for a microSec which creates
      // a bad user experience
      position: 'absolute',
      left: '-9999px',
      fontSize: testSize,
    };
    var h = document.body;
    // div to load spans for the base fonts
    var baseFontsDiv = document.createElement('div');
    // div to load spans for the fonts to detect
    var fontsDiv = document.createElement('div');
    var defaultWidth = {};
    var defaultHeight = {};
    // creates a span where the fonts will be loaded
    var createSpan = function () {
      var span = document.createElement('span');
      span.textContent = testString;
      for (var _i = 0, _a = Object.keys(fontSpanStyle); _i < _a.length; _i++) {
        var prop = _a[_i];
        span.style[prop] = fontSpanStyle[prop];
      }
      return span;
    };
    // creates a span and load the font to detect and a base font for fallback
    var createSpanWithFonts = function (fontToDetect, baseFont) {
      var s = createSpan();
      s.style.fontFamily = "'" + fontToDetect + "'," + baseFont;
      return s;
    };
    // creates spans for the base fonts and adds them to baseFontsDiv
    var initializeBaseFontsSpans = function () {
      return baseFonts.map(function (baseFont) {
        var s = createSpan();
        s.style.fontFamily = baseFont;
        baseFontsDiv.appendChild(s);
        return s;
      });
    };
    // creates spans for the fonts to detect and adds them to fontsDiv
    var initializeFontsSpans = function () {
      // Stores {fontName : [spans for that font]}
      var spans = {};
      var _loop_1 = function (font) {
        spans[font] = baseFonts.map(function (baseFont) {
          var s = createSpanWithFonts(font, baseFont);
          fontsDiv.appendChild(s);
          return s;
        });
      };
      for (var _i = 0, fontList_1 = fontList; _i < fontList_1.length; _i++) {
        var font = fontList_1[_i];
        _loop_1(font);
      }
      return spans;
    };
    // checks if a font is available
    var isFontAvailable = function (fontSpans) {
      return baseFonts.some(function (baseFont, baseFontIndex) {
        return fontSpans[baseFontIndex].offsetWidth !== defaultWidth[baseFont] ||
          fontSpans[baseFontIndex].offsetHeight !== defaultHeight[baseFont];
      });
    };

    var available = [];
    try {
      // create spans for base fonts
      var baseFontsSpans = initializeBaseFontsSpans();
      // add the spans to the DOM
      h.appendChild(baseFontsDiv);
      // get the default width for the three base fonts
      for (var index = 0, length_1 = baseFonts.length; index < length_1; index++) {
        defaultWidth[baseFonts[index]] = baseFontsSpans[index].offsetWidth; // width for the default font
        defaultHeight[baseFonts[index]] = baseFontsSpans[index].offsetHeight; // height for the default font
      }
      // create spans for fonts to detect
      var fontsSpans = initializeFontsSpans();
      // add all the spans to the DOM
      h.appendChild(fontsDiv);
      // check available fonts
      for (var i = 0, l = fontList.length; i < l; i++) {
        if (isFontAvailable(fontsSpans[fontList[i]])) {
          available.push(fontList[i]);
        }
      }
      // remove spans from DOM
      h.removeChild(fontsDiv);
      h.removeChild(baseFontsDiv);
    } catch (e) {
      console.error('no support get fonts')
    }

    return available;
  },
  x64hash128: function (key, seed) {
    key = key || ''
    seed = seed || 0
    var remainder = key.length % 16
    var bytes = key.length - remainder
    var h1 = [0, seed]
    var h2 = [0, seed]
    var k1 = [0, 0]
    var k2 = [0, 0]
    var c1 = [0x87c37b91, 0x114253d5]
    var c2 = [0x4cf5ad43, 0x2745937f]
    for (var i = 0; i < bytes; i = i + 16) {
      k1 = [((key.charCodeAt(i + 4) & 0xff)) | ((key.charCodeAt(i + 5) & 0xff) << 8) | ((key.charCodeAt(i + 6) & 0xff) << 16) | ((key.charCodeAt(i + 7) & 0xff) << 24), ((key.charCodeAt(i) & 0xff)) | ((key.charCodeAt(i + 1) & 0xff) << 8) | ((key.charCodeAt(i + 2) & 0xff) << 16) | ((key.charCodeAt(i + 3) & 0xff) << 24)]
      k2 = [((key.charCodeAt(i + 12) & 0xff)) | ((key.charCodeAt(i + 13) & 0xff) << 8) | ((key.charCodeAt(i + 14) & 0xff) << 16) | ((key.charCodeAt(i + 15) & 0xff) << 24), ((key.charCodeAt(i + 8) & 0xff)) | ((key.charCodeAt(i + 9) & 0xff) << 8) | ((key.charCodeAt(i + 10) & 0xff) << 16) | ((key.charCodeAt(i + 11) & 0xff) << 24)]
      k1 = this.x64Multiply(k1, c1)
      k1 = this.x64Rotl(k1, 31)
      k1 = this.x64Multiply(k1, c2)
      h1 = this.x64Xor(h1, k1)
      h1 = this.x64Rotl(h1, 27)
      h1 = this.x64Add(h1, h2)
      h1 = this.x64Add(this.x64Multiply(h1, [0, 5]), [0, 0x52dce729])
      k2 = this.x64Multiply(k2, c2)
      k2 = this.x64Rotl(k2, 33)
      k2 = this.x64Multiply(k2, c1)
      h2 = this.x64Xor(h2, k2)
      h2 = this.x64Rotl(h2, 31)
      h2 = this.x64Add(h2, h1)
      h2 = this.x64Add(this.x64Multiply(h2, [0, 5]), [0, 0x38495ab5])
    }
    k1 = [0, 0]
    k2 = [0, 0]
    switch (remainder) {
      case 15:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 14)], 48))
      // fallthrough
      case 14:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 13)], 40))
      // fallthrough
      case 13:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 12)], 32))
      // fallthrough
      case 12:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 11)], 24))
      // fallthrough
      case 11:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 10)], 16))
      // fallthrough
      case 10:
        k2 = this.x64Xor(k2, this.x64LeftShift([0, key.charCodeAt(i + 9)], 8))
      // fallthrough
      case 9:
        k2 = this.x64Xor(k2, [0, key.charCodeAt(i + 8)])
        k2 = this.x64Multiply(k2, c2)
        k2 = this.x64Rotl(k2, 33)
        k2 = this.x64Multiply(k2, c1)
        h2 = this.x64Xor(h2, k2)
      // fallthrough
      case 8:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 7)], 56))
      // fallthrough
      case 7:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 6)], 48))
      // fallthrough
      case 6:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 5)], 40))
      // fallthrough
      case 5:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 4)], 32))
      // fallthrough
      case 4:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 3)], 24))
      // fallthrough
      case 3:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 2)], 16))
      // fallthrough
      case 2:
        k1 = this.x64Xor(k1, this.x64LeftShift([0, key.charCodeAt(i + 1)], 8))
      // fallthrough
      case 1:
        k1 = this.x64Xor(k1, [0, key.charCodeAt(i)])
        k1 = this.x64Multiply(k1, c1)
        k1 = this.x64Rotl(k1, 31)
        k1 = this.x64Multiply(k1, c2)
        h1 = this.x64Xor(h1, k1)
      // fallthrough
    }
    h1 = this.x64Xor(h1, [0, key.length])
    h2 = this.x64Xor(h2, [0, key.length])
    h1 = this.x64Add(h1, h2)
    h2 = this.x64Add(h2, h1)
    h1 = this.x64Fmix(h1)
    h2 = this.x64Fmix(h2)
    h1 = this.x64Add(h1, h2)
    h2 = this.x64Add(h2, h1)
    return ('00000000' + (h1[0] >>> 0).toString(16)).slice(-8) + ('00000000' + (h1[1] >>> 0).toString(16)).slice(-8) + ('00000000' + (h2[0] >>> 0).toString(16)).slice(-8) + ('00000000' + (h2[1] >>> 0).toString(16)).slice(-8)
  },
  /// MurmurHash3 related functions

  //
  // Given two 64bit ints (as an array of two 32bit ints) returns the two
  // added together as a 64bit int (as an array of two 32bit ints).
  //
  x64Add: function (m, n) {
    m = [m[0] >>> 16, m[0] & 0xffff, m[1] >>> 16, m[1] & 0xffff]
    n = [n[0] >>> 16, n[0] & 0xffff, n[1] >>> 16, n[1] & 0xffff]
    var o = [0, 0, 0, 0]
    o[3] += m[3] + n[3]
    o[2] += o[3] >>> 16
    o[3] &= 0xffff
    o[2] += m[2] + n[2]
    o[1] += o[2] >>> 16
    o[2] &= 0xffff
    o[1] += m[1] + n[1]
    o[0] += o[1] >>> 16
    o[1] &= 0xffff
    o[0] += m[0] + n[0]
    o[0] &= 0xffff
    return [(o[0] << 16) | o[1], (o[2] << 16) | o[3]]
  },

  //
  // Given two 64bit ints (as an array of two 32bit ints) returns the two
  // multiplied together as a 64bit int (as an array of two 32bit ints).
  //
  x64Multiply: function (m, n) {
    m = [m[0] >>> 16, m[0] & 0xffff, m[1] >>> 16, m[1] & 0xffff]
    n = [n[0] >>> 16, n[0] & 0xffff, n[1] >>> 16, n[1] & 0xffff]
    var o = [0, 0, 0, 0]
    o[3] += m[3] * n[3]
    o[2] += o[3] >>> 16
    o[3] &= 0xffff
    o[2] += m[2] * n[3]
    o[1] += o[2] >>> 16
    o[2] &= 0xffff
    o[2] += m[3] * n[2]
    o[1] += o[2] >>> 16
    o[2] &= 0xffff
    o[1] += m[1] * n[3]
    o[0] += o[1] >>> 16
    o[1] &= 0xffff
    o[1] += m[2] * n[2]
    o[0] += o[1] >>> 16
    o[1] &= 0xffff
    o[1] += m[3] * n[1]
    o[0] += o[1] >>> 16
    o[1] &= 0xffff
    o[0] += (m[0] * n[3]) + (m[1] * n[2]) + (m[2] * n[1]) + (m[3] * n[0])
    o[0] &= 0xffff
    return [(o[0] << 16) | o[1], (o[2] << 16) | o[3]]
  },
  //
  // Given a 64bit int (as an array of two 32bit ints) and an int
  // representing a number of bit positions, returns the 64bit int (as an
  // array of two 32bit ints) rotated left by that number of positions.
  //
  x64Rotl: function (m, n) {
    n %= 64
    if (n === 32) {
      return [m[1], m[0]]
    } else if (n < 32) {
      return [(m[0] << n) | (m[1] >>> (32 - n)), (m[1] << n) | (m[0] >>> (32 - n))]
    } else {
      n -= 32
      return [(m[1] << n) | (m[0] >>> (32 - n)), (m[0] << n) | (m[1] >>> (32 - n))]
    }
  },
  //
  // Given a 64bit int (as an array of two 32bit ints) and an int
  // representing a number of bit positions, returns the 64bit int (as an
  // array of two 32bit ints) shifted left by that number of positions.
  //
  x64LeftShift: function (m, n) {
    n %= 64
    if (n === 0) {
      return m
    } else if (n < 32) {
      return [(m[0] << n) | (m[1] >>> (32 - n)), m[1] << n]
    } else {
      return [m[1] << (n - 32), 0]
    }
  },
  //
  // Given two 64bit ints (as an array of two 32bit ints) returns the two
  // xored together as a 64bit int (as an array of two 32bit ints).
  //
  x64Xor: function (m, n) {
    return [m[0] ^ n[0], m[1] ^ n[1]]
  },
  //
  // Given a block, returns murmurHash3's final x64 mix of that block.
  // (`[0, h[0] >>> 1]` is a 33 bit unsigned right shift. This is the
  // only place where we need to right shift 64bit ints.)
  //
  x64Fmix: function (h) {
    h = this.x64Xor(h, [0, h[0] >>> 1])
    h = this.x64Multiply(h, [0xff51afd7, 0xed558ccd])
    h = this.x64Xor(h, [0, h[0] >>> 1])
    h = this.x64Multiply(h, [0xc4ceb9fe, 0x1a85ec53])
    h = this.x64Xor(h, [0, h[0] >>> 1])
    return h
  },
  /**
   * navigator.cookieEnabled cannot detect custom or nuanced cookie blocking configurations. For example, when blocking
   * cookies via the Advanced Privacy Settings in IE9, it always returns true. And there have been issues in the past with
   * site-specific exceptions. Don't rely on it.
   *
   * @see https://github.com/Modernizr/Modernizr/blob/master/feature-detects/cookies.js Taken from here
   */
  IsCookiesEnabled: function () {
    var d = document;
    // Taken from here: https://github.com/Modernizr/Modernizr/blob/master/feature-detects/cookies.js
    // navigator.cookieEnabled cannot detect custom or nuanced cookie blocking configurations. For example, when blocking
    // cookies via the Advanced Privacy Settings in IE9, it always returns true. And there have been issues in the past
    // with site-specific exceptions. Don't rely on it.
    // try..catch because some in situations `document.cookie` is exposed but throws a
    // SecurityError if you try to access it; e.g. documents created from data URIs
    // or in sandboxed iframes (depending on flags/context)
    try {
      // Create cookie
      d.cookie = 'cookietest=1; SameSite=Strict;';
      var result = d.cookie.indexOf('cookietest=') !== -1;
      // Delete cookie
      d.cookie = 'cookietest=1; SameSite=Strict; expires=Thu, 01-Jan-1970 00:00:01 GMT';
      return result;
    }
    catch (e) {
      return -1;
    }
  },
  /**
 * 判断图片是否加载
 * @returns {number}
 */
getImageLoad : function () {
  window.ish = 0
  const delImg = function () {
    if (window.ish > 0) {
      return window.ish;
    }
    window.ish = 1
    const im = document.getElementById('probe-image')
    if (im.width === 122 && im.height === 110) {
      window.ish = 2;
    }
    try {
      im.remove()
    } catch (e) {
      try {
        im.parentNode.removeChild(im);
      } catch (e) { }
    }
  }
  const im = document.getElementById('probe-image')
  var image = im || document.createElement("img");
  image.src = this.getCanvasFp();
  image.setAttribute("id", "probe-image");
  image.style.display = 'none';
  image.onerror = delImg
  image.onload = delImg
  image.oncancel = delImg
  image.oninvalid = delImg
  document.body.appendChild(image);
  return delImg();
}

};
