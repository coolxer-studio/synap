/**
 * 断点调试判断
 * @param msgCallback
 */
export default function debugMonitor(msgCallback) {
  // 是否调试
  window.isDebug = 0;

  // 定义debug上报数据类型
  let debugMsg = {
    type: "DebugData",
    debugType: 0, // 1、2
    diff: 0 // 时间差
  };

  var debugMsgCallback = function (debugMsg) {
    msgCallback(debugMsg);
  }

    /**
     * 检测断点-1
     */
    // (function () { return ![]; }.constructor('window.abc = function() {' +
    //   '    setInterval(function() {' +
    //   '      var curTime = new Date().getTime();' +
    //   ' console.log("1111:"+curTime);'+
    //   '      postMessage(curTime);' +
    //   '    },1e4)' +
    //   '  }').call("stateObject"));
  // 等效代码
   window.abc = function() {
    setInterval(function() {
      const curTime = new Date().getTime();
      postMessage (curTime);
    },1e4)
  }
   

  try {
    const worker = new Worker(URL.createObjectURL(new Blob([`(${window.abc.toString()})()`])));
    window.abc = null;
    worker.onmessage = (messageEvent) => {
      const curTime = new Date().getTime();
      var diff = curTime - messageEvent.data;
      if (window.isDebug == 0 && diff > 5000 && document.visibilityState === 'visible') {
        debugMsg.debugType = 1;
        debugMsg.diff = diff;
        debugMsgCallback(debugMsg);
        window.isDebug = 1;
      } else {
        // 非调试状态
        window.isDebug = 0;
      }
    };
  } catch (e) {
    console.error('no support worker')
    window.abc = null;
  }

  /**
   * 检测断点-2
   */
  (function () { return ![]; }.constructor('window.abc = function () {' +
    '    setTimeout(function () {' +
    '      (function() {' +
    '        return ![];' +
    '      }.constructor("debu" + "gger").call("stateObject"));' +
    '      var curTime = new Date().getTime();' +
    ' console.log("2222:"+curTime);'+
    '      postMessage(curTime);' +
    '    }, 3e5);' +
    '  }').call("stateObject"));
  /**
   * 等效代码
     window.abc = function () {
      setTimeout(function () {
        (function () {
          return ![];
        }.constructor("debu" + "gger").call("stateObject"));
        const curTime = new Date().getTime();
        postMessage(curTime);
      }, 3e5);
    }
   */
  try {
    const gan = new Worker(URL.createObjectURL(new Blob([`(${window.abc.toString()})()`])));
    window.abc = null;
    gan.onmessage = (messageEvent) => {
      const curTime = new Date().getTime();
      var diff = curTime - messageEvent.data;
      if (window.isDebug == 0 && diff > 5000 && document.visibilityState === 'visible') {
        debugMsg.debugType = 2;
        debugMsg.diff = diff;
        debugMsgCallback(debugMsg);
        window.isDebug = 1;
      } else {
        // 非调试状态
        window.isDebug = 0;
      }
    };
  } catch (e) {
    console.log('no support worker')
    window.abc = null;
  }


}
