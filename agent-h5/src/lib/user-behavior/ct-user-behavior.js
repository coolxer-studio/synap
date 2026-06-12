import ctEventListener from './event-listener'
import ctDevToolListener from './devtool-listener'
window.ct_event_config = {
    "event": ["pageshow", "pagehide", "click", "input", "mousemove", "touchstart", "touchend", "mousedown", "mouseup"]
};

window.ct_dev_tool_config = {
    "shortCut": [".*"],
    "windowSise": [".*"]
};

export default {

    devToolMonitor: function (msgCallback) {
        ctDevToolListener(msgCallback);
    },
    eventMonitor: function (msgCallback) {
        ctEventListener(msgCallback);
    },
    loadConfig: function (config) {
        window.ct_event_config = config['eventConfig'];
        window.ct_dev_tool_config = config['devToolConfig'];
    }

}