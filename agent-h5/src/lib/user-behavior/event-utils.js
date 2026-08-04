export function getTouchXY(event, body = document.body) {
  const touch = event && event.changedTouches && event.changedTouches[0];
  if (!touch) return { x: 0, y: 0 };
  if (touch.pageX != null || touch.pageY != null) {
    return { x: touch.pageX || 0, y: touch.pageY || 0 };
  }
  const safeBody = body || { scrollLeft: 0, scrollTop: 0, clientLeft: 0, clientTop: 0 };
  return {
    x: (touch.clientX || 0) + safeBody.scrollLeft - safeBody.clientLeft,
    y: (touch.clientY || 0) + safeBody.scrollTop - safeBody.clientTop
  };
}

