// 全局事件监听函数
import agLocalStore from "../ag-localstore";

const mousePosition = (evt) => {
  evt = evt || window.event;
  if (evt.pageX || evt.pageY) {
    return { x: evt.pageX, y: evt.pageY };
  }
  return {
    x: evt.clientX + document.body.scrollLeft - document.body.clientLeft,
    y: evt.clientY + document.body.scrollTop - document.body.clientTop
  };
}

const getElementPosition = function (e) {
  let x = 0, y = 0;
  while (e != null) {
    x += e.offsetLeft;
    y += e.offsetTop;
    e = e.offsetParent;
  }
  return { x: x, y: y };
};


const getTouchXY = (eve) => {
  if (eve.changedTouches.length >= 1) {
    const touch = eve.changedTouches[0]
    if (touch.pageX || touch.pageY) {
      return [touch.pageX, touch.pageY]
    }
    return {
      x: touch.clientX + document.body.scrollLeft - document.body.clientLeft,
      y: touch.clientY + document.body.scrollTop - document.body.clientTop
    }

  }
}

export default function eventListener(msgCallback) {
  let eventMsg = {
    type: 'EventData',
    href: window.location.href, // href
    docTitle: document.title, // 标题
    buildTime: "" + Date.now(), // timestamp
    online: navigator.onLine ? 1 : 0, //是否在线 // is_online
    data: {}
  }

  var eventMsgCallback = function (event) {
    // 处理事件消息
    var target = event.target || event.srcElement;
    eventMsg.data.type = event.type;
    switch (event.type) {
      case 'pageshow':
        eventMsg.data.href = agLocalStore.get('h_from_href') || '';
        agLocalStore.set('h_from_href', window.location.href);
        break;
      case 'pagehide':
      // 穿透到unload
      case 'unload':
        eventMsg.data.href = target instanceof HTMLDocument ? (target.activeElement && target.activeElement.href ? target.activeElement.href : '') : '';  // tp
        eventMsg.data.action = target.activeElement instanceof HTMLBodyElement ? 'close' : 'jump';  // a
        eventMsg.data.targetId = target instanceof HTMLDocument && target.activeElement ? target.activeElement.id : '';  // loc
        eventMsg.data.nodeName = target instanceof HTMLDocument && target.activeElement ? target.activeElement.nodeName : ''; // desc
        break;
      case "click":
        eventMsg.data.targetId = target ? target.id : ''; // loc
        eventMsg.data.nodeName = target ? target.nodeName : ''; // desc
        var mp = mousePosition(event);
        eventMsg.data.x = mp.x; // x
        eventMsg.data.y = mp.y; // y
        eventMsg.data.scrollLeft = parseInt(event.scrollLeft) || 0;
        eventMsg.data.scrollTop = parseInt(event.scrollTop) || 0;
        eventMsg.data.clientX = parseInt(event.clientX) || 0;
        eventMsg.data.clientY = parseInt(event.clientY) || 0;
        eventMsg.data.offsetX = parseInt(event.offsetX) || 0;
        eventMsg.data.offsetY = parseInt(event.offsetY) || 0;
        eventMsg.data.offsetWidth = parseInt(event.target.offsetWidth) || 0;
        eventMsg.data.offsetHeight = parseInt(event.target.offsetHeight) || 0;
        eventMsg.data.offsetTop = parseInt(event.target.offsetTop) || 0;
        eventMsg.data.offsetLeft = parseInt(event.target.offsetLeft) || 0;
        eventMsg.data.screenWidth = window.screen.width || 0;
        eventMsg.data.screenHeight = window.screen.height || 0;
        break;
      case "mouseup":
        eventMsg.data.targetId = target ? target.id : ''; // loc
        eventMsg.data.nodeName = target ? target.nodeName : ''; // desc
        var mp = mousePosition(event);
        eventMsg.data.x = mp.x; // x
        eventMsg.data.y = mp.y; // y
        eventMsg.data.scrollLeft = parseInt(event.scrollLeft) || 0;
        eventMsg.data.scrollTop = parseInt(event.scrollTop) || 0;
        eventMsg.data.clientX = parseInt(event.clientX) || 0;
        eventMsg.data.clientY = parseInt(event.clientY) || 0;
        eventMsg.data.offsetX = parseInt(event.offsetX) || 0;
        eventMsg.data.offsetY = parseInt(event.offsetY) || 0;
        eventMsg.data.offsetWidth = parseInt(event.target.offsetWidth) || 0;
        eventMsg.data.offsetHeight = parseInt(event.target.offsetHeight) || 0;
        eventMsg.data.offsetTop = parseInt(event.target.offsetTop) || 0;
        eventMsg.data.offsetLeft = parseInt(event.target.offsetLeft) || 0;
        eventMsg.data.screenWidth = window.screen.width || 0;
        eventMsg.data.screenHeight = window.screen.height || 0;
        break;
      case "mousedown":
        eventMsg.data.targetId = target ? target.id : ''; // loc
        eventMsg.data.nodeName = target ? target.nodeName : ''; // desc
        var mp = mousePosition(event);
        eventMsg.data.x = mp.x; // x
        eventMsg.data.y = mp.y; // y
        break;
      case "input":
        eventMsg.data.inputType = event.inputType ? event.inputType : '';  // type
        eventMsg.data.targetId = target ? target.id : '';
        eventMsg.data.nodeName = target ? target.nodeName : '';
        eventMsg.data.x = target ? getElementPosition(target).x : '';
        eventMsg.data.y = target ? getElementPosition(target).y : '';
        eventMsg.data.clientWidth = target ? target.clientWidth : '';
        eventMsg.data.clientHeight = target ? target.clientHeight : '';
        break;
      case "mousemove":
        var mp = mousePosition(event);
        eventMsg.data.x = mp.x; // x
        eventMsg.data.y = mp.y; // y
        if(window.mousemove_count){
          if(window.mousemove_count<50){
            window.mousemove_count++;
            return;
          }else{
            window.mousemove_count = 1;
          }
        }else{
          window.mousemove_count = 1; 
        }
        break;
      case "touchstart":
        var touchXY = getTouchXY(event)
        eventMsg.data.x = touchXY.x;
        eventMsg.data.y = touchXY.y;
        break;
      case "touchend":
        touchXY = getTouchXY(event);
        eventMsg.data.targetId = target ? target.id : '';
        eventMsg.data.nodeName = target ? target.nodeName : '';
        eventMsg.data.x = touchXY.x;
        eventMsg.data.y = touchXY.y;

        var ele = event.changedTouches[0];
        eventMsg.data.clientX = ele.clientX || 0;
        eventMsg.data.clientY = ele.clientY || 0;
        eventMsg.data.radiusX = ele.radiusX || 0;
        eventMsg.data.radiusY = ele.radiusY || 0;
        eventMsg.data.rotationAngle = ele.rotationAngle || 0;
        eventMsg.data.force = ele.force || 0;
        eventMsg.data.screenWidth = window.screen.width || 0;
        eventMsg.data.screenHeight = window.screen.height || 0;
        break;
      default:
        break;
    }
    // console.log('UB-event:', event.type, event);
    msgCallback(eventMsg);
  }




  var addListener = function (eventMsgCallback) {
    const listenerTypeArray = window.ct_event_config['event'];
    listenerTypeArray.forEach(function (listenerType) {
      if (typeof window['on' + listenerType] !== 'undefined') {
        window.addEventListener(listenerType, eventMsgCallback);
      } else {
        // 不支持处理
        if ('pagehide' === listenerType) {
          window.addEventListener("unload", eventMsgCallback);
        }
      }
    });
  }

  addListener(eventMsgCallback);
  // dom变化处理
  try {
    var domObserver = new MutationSummary({
      callback: function (summaries) {
        addListener(eventMsgCallback);
      },
      queries: [{
        element: '*'
      }]
    });
  } catch (e) {
    console.warn('no support observer dom')
  }


}
