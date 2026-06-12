import agLocalStore from "../ag-localstore";
/**
 * 检测是否打开了开发者工具
 * @param msgCallback
 * @param w 窗口变化阈值
 */
export default function devToolListener(msgCallback, w = 200) {
  // 是否已经触发控制台开启
  window.hasDevTool = 0;

  let devToolMsg = {
    type: 'DevToolData',
    data: {}
  }

  var devToolMsgCallback = function (devToolMsg) {
    msgCallback(devToolMsg);
  }

  /**
   * 监听快捷键打开控制台
   * @type {Document}
   */

  const shortcutListener = function (event) {
    let code;
    if (event.keyCode) {
      code = event.keyCode;
    } else if (event.which) {
      code = event.which;
    }
    if ((event.ctrlKey || event.metaKey) && event.altKey && String.fromCharCode(code).toLowerCase() === 'i') {
      devToolMsg.data.key = event.ctrlKey ? 'event.ctrlKey' : 'event.metaKey';
      devToolMsgCallback(devToolMsg);
    } else if (code === 123) {
      // F12
      devToolMsg.data.key = 'f12';
      devToolMsgCallback(devToolMsg);
    }
  }
  if (window.ct_dev_tool_config['shortCut']) {
    if (document.addEventListener) {
      document.addEventListener('keydown', shortcutListener, false);
    } else if (document.attachEvent) {
      document.attachEvent('onkeydown', shortcutListener);
    } else {
      document['onkeydown'] = shortcutListener;
    }
  }

  /**
   * 监听窗口大小变化
   */
  const windowSizeListener = function () {
    const threshold = w;
    const widthThreshold = window.outerWidth - window.innerWidth > threshold;
    const heightThreshold = window.outerHeight - window.innerHeight > threshold;
    if (!window.hasDevTool && (widthThreshold || heightThreshold)) {
      devToolMsg.data.outerWidth = window.outerWidth;
      devToolMsg.data.innerWidth = window.innerWidth;
      devToolMsg.data.outerHeight = window.outerHeight;
      devToolMsg.data.innerHeight = window.innerHeight;
      devToolMsgCallback(devToolMsg);
      window.hasDevTool = 1
    } else {
      window.hasDevTool = 0;
    }
  }
  if (window.ct_dev_tool_config['shortCut']) {
    windowSizeListener();
    window.addEventListener('resize', windowSizeListener);
  }
  /**
   * 监听console重新实例化
   */
  /*
  (() => {
    let cpt = 0;
    const regexp = /./;
    regexp.toString = () => {
      cpt++;
      return 'spooky';
    };
    console.debug(regexp);
    alert(cpt)
    return cpt > 1;
  })();
  */


  /**
   * 监听console计算性能
   */
  /*
  const checkConsole = (n) => {
    const ct = performance.now();
    for (let i = 0; i < n; i++) {
      console.debug(performance.now());
      console.clear()
    }
    const ce = performance.now();
    const diff = ce - ct;
    return diff
  }

  const checkPerformance = function () {
    try {
      const doFirst = checkConsole(100);
      const doSecond = checkConsole(300);
      if (doSecond > 200 || doFirst >= 100) { // 耗时过长直接认为是启动了开发工具
        sendEvent(sendEvent(doFirst + ',' + doSecond + '_1'));
      } else if (doFirst < 30 && doSecond > 30) {
        const serial = agLocalStore.get("h_five_serial") || 1;
        if (serial > 1 && doFirst > 10 && doSecond - doFirst > 25) { // 耗时差值超过阈值，认为是开启了开发工具
          sendEvent(sendEvent(doFirst + ',' + doSecond + '_3'));
        }
      } else if (doFirst > 30) {
        if (doSecond - doFirst > 100) {
          sendEvent(sendEvent(doFirst + ',' + doSecond + '_2'));
        }
      }
    } catch (e) {
      console.error('no support performance', e)
    }
  };
  if (window.hasDevTool === 0) {
    setTimeout(checkPerformance, 1234);
  }
  */

}
