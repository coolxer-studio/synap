import baseData from '../base-data';

export default function devToolListener(msgCallback, threshold = 200) {
  let hasDevTool = false;
  const cleanups = [];

  const emit = (data) => msgCallback({
    type: 'DevToolData',
    buildDate: baseData.currentFormatDate(),
    data: { ...data }
  });

  const shortcutListener = function (event) {
    const code = event.keyCode || event.which;
    if ((event.ctrlKey || event.metaKey) && event.altKey && String.fromCharCode(code).toLowerCase() === 'i') {
      emit({ key: event.ctrlKey ? 'event.ctrlKey' : 'event.metaKey' });
    } else if (code === 123) {
      emit({ key: 'f12' });
    }
  };

  const windowSizeListener = function () {
    const widthThreshold = window.outerWidth - window.innerWidth > threshold;
    const heightThreshold = window.outerHeight - window.innerHeight > threshold;
    const detected = widthThreshold || heightThreshold;
    if (!hasDevTool && detected) {
      emit({
        outerWidth: window.outerWidth,
        innerWidth: window.innerWidth,
        outerHeight: window.outerHeight,
        innerHeight: window.innerHeight
      });
    }
    hasDevTool = detected;
  };

  const config = window.ct_dev_tool_config || {};
  if (config.shortCut) {
    document.addEventListener('keydown', shortcutListener, false);
    cleanups.push(() => document.removeEventListener('keydown', shortcutListener, false));
  }
  if (config.windowSise || config.windowSize || config.shortCut) {
    windowSizeListener();
    window.addEventListener('resize', windowSizeListener);
    cleanups.push(() => window.removeEventListener('resize', windowSizeListener));
  }

  return function cleanup() {
    cleanups.forEach((fn) => fn());
  };
}
