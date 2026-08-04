import agLocalStore from '../ag-localstore';
import baseData from '../base-data';
import { getTouchXY } from './event-utils';

const mousePosition = (event) => {
  if (event.pageX != null || event.pageY != null) {
    return { x: event.pageX || 0, y: event.pageY || 0 };
  }
  const body = document.body || { scrollLeft: 0, scrollTop: 0, clientLeft: 0, clientTop: 0 };
  return {
    x: (event.clientX || 0) + body.scrollLeft - body.clientLeft,
    y: (event.clientY || 0) + body.scrollTop - body.clientTop
  };
};

const getElementPosition = (element) => {
  let x = 0;
  let y = 0;
  while (element) {
    x += element.offsetLeft || 0;
    y += element.offsetTop || 0;
    element = element.offsetParent;
  }
  return { x, y };
};

function elementData(target) {
  return {
    targetId: target && target.id ? target.id : '',
    nodeName: target && target.nodeName ? target.nodeName : ''
  };
}

function pointerData(event, target) {
  const position = mousePosition(event);
  return {
    ...elementData(target),
    x: position.x,
    y: position.y,
    scrollLeft: Number(event.scrollLeft) || 0,
    scrollTop: Number(event.scrollTop) || 0,
    clientX: Number(event.clientX) || 0,
    clientY: Number(event.clientY) || 0,
    offsetX: Number(event.offsetX) || 0,
    offsetY: Number(event.offsetY) || 0,
    offsetWidth: target ? Number(target.offsetWidth) || 0 : 0,
    offsetHeight: target ? Number(target.offsetHeight) || 0 : 0,
    offsetTop: target ? Number(target.offsetTop) || 0 : 0,
    offsetLeft: target ? Number(target.offsetLeft) || 0 : 0,
    screenWidth: window.screen ? window.screen.width || 0 : 0,
    screenHeight: window.screen ? window.screen.height || 0 : 0
  };
}

export default function eventListener(msgCallback, { mousemoveInterval = 500 } = {}) {
  const listenerTypes = Array.isArray(window.ct_event_config && window.ct_event_config.event)
    ? window.ct_event_config.event
    : [];
  const attached = [];
  let lastMousemoveAt = 0;
  let lastTouch = null;

  const callback = function (event) {
    const now = Date.now();
    if (event.type === 'mousemove' && now - lastMousemoveAt < mousemoveInterval) return;
    if (event.type === 'mousemove') lastMousemoveAt = now;

    const target = event.target || event.srcElement;
    if (event.type === 'click' && lastTouch && now - lastTouch.at < 700 && lastTouch.target === target) return;
    let data = { type: event.type, trusted: event.isTrusted !== false };
    switch (event.type) {
      case 'pageshow':
        data.href = agLocalStore.get('h_from_href') || '';
        agLocalStore.set('h_from_href', window.location.href);
        break;
      case 'pagehide':
      case 'unload': {
        const documentTarget = typeof HTMLDocument !== 'undefined' && target instanceof HTMLDocument;
        const active = documentTarget ? target.activeElement : null;
        data = {
          ...data,
          href: active && active.href ? active.href : '',
          action: typeof HTMLBodyElement !== 'undefined' && active instanceof HTMLBodyElement ? 'close' : 'jump',
          targetId: active && active.id ? active.id : '',
          nodeName: active && active.nodeName ? active.nodeName : ''
        };
        break;
      }
      case 'click':
      case 'mouseup':
        data = { ...data, ...pointerData(event, target) };
        break;
      case 'mousedown':
      case 'mousemove': {
        const point = mousePosition(event);
        data = { ...data, ...elementData(target), x: point.x, y: point.y };
        break;
      }
      case 'input': {
        const position = target ? getElementPosition(target) : { x: '', y: '' };
        data = {
          ...data,
          ...elementData(target),
          inputType: event.inputType || '',
          x: position.x,
          y: position.y,
          clientWidth: target ? target.clientWidth : '',
          clientHeight: target ? target.clientHeight : ''
        };
        break;
      }
      case 'touchstart':
      case 'touchend': {
        const point = getTouchXY(event);
        const touch = event.changedTouches && event.changedTouches[0];
        data = { ...data, ...elementData(target), x: point.x, y: point.y };
        if (event.type === 'touchend' && touch) {
          Object.assign(data, {
            clientX: touch.clientX || 0,
            clientY: touch.clientY || 0,
            radiusX: touch.radiusX || 0,
            radiusY: touch.radiusY || 0,
            rotationAngle: touch.rotationAngle || 0,
            force: touch.force || 0,
            screenWidth: window.screen ? window.screen.width || 0 : 0,
            screenHeight: window.screen ? window.screen.height || 0 : 0
          });
        }
        break;
      }
      default:
        break;
    }

    msgCallback({
      type: 'EventData',
      href: window.location.href,
      docTitle: document.title,
      buildDate: baseData.currentFormatDate(),
      online: navigator.onLine ? 1 : 0,
      data
    });
  };

  const rememberTouch = (event) => {
    if (event.pointerType === 'touch') lastTouch = { at: Date.now(), target: event.target };
  };

  window.addEventListener('pointerup', rememberTouch, { passive: true });
  attached.push({ type: 'pointerup', callback: rememberTouch });

  listenerTypes.forEach((type) => {
    if (['mousemove', 'mousedown', 'mouseup', 'touchstart', 'touchend'].includes(type)) return;
    const actualType = type === 'pagehide' && typeof window.onpagehide === 'undefined' ? 'unload' : type;
    if (attached.some((item) => item.type === actualType)) return;
    window.addEventListener(actualType, callback, { passive: actualType === 'touchstart' || actualType === 'touchend' });
    attached.push({ type: actualType, callback });
  });

  return function cleanup() {
    attached.forEach(({ type, callback: listener }) => window.removeEventListener(type, listener));
  };
}
