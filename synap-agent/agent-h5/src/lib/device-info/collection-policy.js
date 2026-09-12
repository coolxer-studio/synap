const CANVAS_METHODS = new Set(['getCanvasFpCRC', 'getCanvasFpCrc']);
const WEBGL_METHODS = new Set(['getWebglVendorAndRenderer']);
const FONT_METHODS = new Set(['getFonts']);

export function applyCollectionProfile(policy, profile) {
  const copy = JSON.parse(JSON.stringify(policy));
  if (!profile.rawEvents && copy.config.eventConfig && Array.isArray(copy.config.eventConfig.event)) {
    copy.config.eventConfig.event = copy.config.eventConfig.event
      .filter((type) => ['pageshow', 'pagehide'].includes(type));
  }
  return copy;
}

export function isDeviceMethodEnabled(section, methodKey, profile = {}) {
  if (section === 'network' && methodKey === 'lanIp') return !!profile.webrtc;
  if (section !== 'baseInfo') return true;
  if (CANVAS_METHODS.has(methodKey)) return !!profile.canvas;
  if (WEBGL_METHODS.has(methodKey)) return !!profile.webgl;
  if (FONT_METHODS.has(methodKey)) return !!profile.fonts;
  if (methodKey === 'getWebglFp') return !!profile.canvas && !!profile.webgl;
  return true;
}

export function collectConfiguredValues(entryArray, configArray, options = {}) {
  const { section = '', profile = {}, invokeFunctions = true } = options;
  return configArray.map((methodKey) => {
    if (!isDeviceMethodEnabled(section, methodKey, profile)) return null;
    let methodValue;
    for (const entry of entryArray) {
      methodValue = entry[methodKey];
      if (typeof methodValue === 'function' && invokeFunctions) {
        methodValue = entry[methodKey]();
      } else if (typeof methodValue === 'function') {
        methodValue = null;
      }
      if (methodValue) break;
    }
    return methodValue;
  });
}
