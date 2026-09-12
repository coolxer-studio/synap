import agLocalStore from "../ag-localstore";
/**
* 判断是否被格式化代码执行
* @returns {Number}
*/
const isFormat = function () { return isFormat.toString().indexOf(String.fromCharCode(10)) > -1 ? 1 : 0; }

// 触发环境检测
export default {
  format: function () {
    return isFormat();
  },
  runType: function () {
    var is_c = 0;
    var wxEnv = agLocalStore.get('_e_c_')
    // var wxEnv = window.localStorage.getItem('_e_c_')
    if (wxEnv) {
      wxEnv = JSON.parse(wxEnv)
      if (wxEnv[0] === 1) {
        return wxEnv[1]
      }
    } else {
      agLocalStore.set("_e_c_", JSON.stringify([0, { '0': [0, 0, -1], '1': [0, 0, -1] }]), 0);
      // window.localStorage.setItem('_e_c_',JSON.stringify([0,{'0': [0,0, -1], '1': [0,0, -1]}]))
    }
    var envOb = [
      [], []
    ];
    if (document.readyState === 'complete') {
      is_c = 1;
      if (typeof __wxjs_environment === 'string' && __wxjs_environment === 'miniprogram' && typeof WeixinJSBridge === 'object') {
        var currNet = JSON.parse(agLocalStore.get('_e_c_'))[1]
        currNet[0] = [1, 1, window.__wxjs_environment ? 1 : 0]
        agLocalStore.set("_e_c_", JSON.stringify([1, currNet]), 0);
        // window.localStorage.setItem('_e_c_',JSON.stringify([1, currNet]))
      }
      if (typeof __WEBVIEW_ID === 'string' && typeof AlipayJSBridge === 'object') {
        var currNet = JSON.parse(agLocalStore.get('_e_c_'))[1]
        currNet[1] = [1, 1, 1]
        agLocalStore.set('_e_c_', JSON.stringify([1, currNet]), 0)
      }
      if (typeof wx !== 'undefined') {
        var BrR = function () {
          wx.getNetworkType({
            complete: function () {
              var currNet = JSON.parse(agLocalStore.get('_e_c_'))[1]
              currNet[0] = [1, 1, 0]
              agLocalStore.set('_e_c_', JSON.stringify([1, currNet]), 0)
            }
          });
        }
        try {
          if (typeof WeixinJSBridge !== "undefined") {
            if (document.addEventListener) {
              document.addEventListener('WeixinJSBridgeReady', BrR, false);
            } else if (document.attachEvent) {
              document.attachEvent('WeixinJSBridgeReady', BrR);
              document.attachEvent('onWeixinJSBridgeReady', BrR);
            }
          } else {
            BrR()
          }
        } catch (e) {
        }
      }
        if (typeof ap !== 'undefined') {
        try {
          ap.getNetworkType(function () {
            var currNet = JSON.parse(agLocalStore.get('_e_c_'))[1]
            currNet[1] = [1, 1, 0]
            agLocalStore.set('_e_c_', JSON.stringify([1, currNet]), 0)
          });
        } catch (e) {
        }
      } else {
        if (typeof my !== 'undefined') {
          try {
            my.getEnv(function (res) {
              var currNet = JSON.parse(agLocalStore.get('_e_c_'))[1]
              currNet[1] = [1, 1, res.miniprogram ? 3 : currNet[1][2]]
              agLocalStore.set('_e_c_', JSON.stringify([1, currNet]), 0)
            });
          } catch (e) {
          }
        }
      }
    }
    var wxEnv = JSON.parse(agLocalStore.get('_e_c_'))
    if (wxEnv && wxEnv[0] === 0 && is_c === 1) {
      agLocalStore.set('_e_c_', JSON.stringify([0, { '0': [1, 0, -1], '1': [1, 0, -1] }]), 0)
    }
    return JSON.parse(agLocalStore.get('_e_c_'))[1]
  },
  /**
 * 判断是否存在自动化工具
 * @returns {number[]}
 */
  getSpider: function () {
    const l = [0, 0, 0, 0], e = window, t = document, v = navigator, n = t.documentElement;
    try {
      if (!!e.__nightmare) {
        l[0] = 1 // [true, ['__nightmare']]
      }
      if ('callPhantom' in e ||
        '_phantom' in e ||
        'phantom' in e ||
        !Function.prototype.bind ||
        Function.prototype.bind.toString().replace(/bind/g, 'Error') != Error.toString() ||
        Function.prototype.toString.toString().replace(/toString/g, 'Error') != Error.toString()
      ) {
        l[1] = 1 // [true, ['phantom']]
      }
      let err;
      try {
        null[3](1);
      } catch (e) {
        err = e;
      }
      if (err.stack.indexOf('phantomjs') > -1) {
        l[1] = 1 // [true, ['phantom']]
      }
      if (

        // !('webdriver' in navigator) ||
        (('webdriver' in v || Object.getOwnPropertyDescriptor(v, 'webdriver')) && v.webdriver == true) ||
        'webdriver' in e || '_Selenium_IDE_Recorder' in e || 'callSelenium' in e ||
        '_selenium' in e || '__webdriver_script_fn' in t || '__driver_evaluate' in t || '__webdriver_evaluate' in t ||
        '__selenium_evaluate' in t || '__fxdriver_evaluate' in t || '__driver_unwrapped' in t ||
        '__webdriver_unwrapped' in t || '__selenium_unwrapped' in t || '__fxdriver_unwrapped' in t ||
        '__webdriver_script_func' in t || n.getAttribute('selenium') !== null ||
        n.getAttribute('driver') !== null ||
        n.getAttribute('webdriver') !== null ||
        (v.webdriver || v.hasOwnProperty('webdriver'))
      ) {
        l[2] = 1 // [true, ['webdriver']]
      }
      try {
        const cg = v.__lookupGetter__('webdriver').toString();
        if ('webdriver' in v && !/.*\[native code].*/g.test(cg)) {
          l[2] = 1 // [true, ['webdriver']]
        }
      } catch (_) { }
      if (e.external && e.external.toString && e.external.toString().indexOf('Sequentum') > -1) {
        l[3] = 1 // [true, ['Sequentum']]
      }
    } catch (e) {
      console.log(e)
    }
    return l
  },
  /**
  * 遍历系统中未知函数内容
  */
  unknownFunctions: function () {
    const knownAutomationGlobals = [
      '__nightmare', 'callPhantom', '_phantom', '__webdriver_script_fn',
      '_Selenium_IDE_Recorder', 'callSelenium', '_selenium', 'Cypress'
    ];
    return knownAutomationGlobals.filter((name) => {
      try { return name in window || name in document; } catch (_) { return false; }
    });
  },

  /**
  * 校验本地信息是否篡改
  * @type {*[]}
  */
  infoModify: function (b, c, d) {
    if (!b || c == null || d == null) return [];
    try { return String(c) === String(d) ? [] : [{ name: String(b).slice(0, 64) }]; }
    catch (_) { return []; }
  }


}
