const LAN_IP_UNAVAILABLE = 'unavailable';
const LAN_IP_MASKED = 'masked';
const LAN_IP_DISABLED = 'disabled';

let lanIpState = {
    status: 'uninitialized',
    addresses: [],
    mdnsMasked: false
};
let pendingDetection = null;

function normalizeAddress(value) {
    if (!value) return '';
    let address = String(value).trim().replace(/^"|"$/g, '');
    if (address[0] === '[') {
        const end = address.indexOf(']');
        if (end > 0) return address.slice(1, end).toLowerCase();
    }
    if (/^\d{1,3}(?:\.\d{1,3}){3}:\d+$/.test(address)) {
        address = address.slice(0, address.lastIndexOf(':'));
    }
    return address.toLowerCase().split('%')[0];
}

export function isPrivateAddress(value) {
    const address = normalizeAddress(value);
    const octets = address.split('.').map((part) => Number(part));
    if (octets.length === 4 && octets.every((part) => Number.isInteger(part) && part >= 0 && part <= 255)) {
        if (octets[0] === 10) return true;
        if (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) return true;
        if (octets[0] === 192 && octets[1] === 168) return true;
        return octets[0] === 169 && octets[1] === 254;
    }
    return /^f[cd][0-9a-f]{2}:/.test(address) || /^fe[89ab][0-9a-f]:/.test(address);
}

export function parseIceCandidate(candidate) {
    if (!candidate) return null;
    const raw = typeof candidate === 'string' ? candidate : candidate.candidate;
    const fields = String(raw || '').trim().split(/\s+/);
    const typeIndex = fields.indexOf('typ');
    const type = candidate.type || (typeIndex >= 0 ? fields[typeIndex + 1] : '');
    const address = normalizeAddress(candidate.address || (fields.length > 4 ? fields[4] : ''));
    if (type !== 'host' || !address) return null;
    if (address.endsWith('.local')) return { address: '', mdnsMasked: true };
    return { address, mdnsMasked: false };
}

function snapshot(status, addresses, mdnsMasked) {
    lanIpState = {
        status,
        addresses: Array.from(addresses || []).sort(),
        mdnsMasked: Boolean(mdnsMasked)
    };
    return { ...lanIpState, addresses: lanIpState.addresses.slice() };
}

function rtcConstructor() {
    return window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection;
}

export function detectLanIp({ enabled = true, timeout = 1200 } = {}) {
    if (!enabled) return Promise.resolve(snapshot(LAN_IP_DISABLED, [], false));
    if (lanIpState.status === 'available') return Promise.resolve(snapshot('available', lanIpState.addresses, lanIpState.mdnsMasked));
    if (pendingDetection) return pendingDetection;

    const RTC = rtcConstructor();
    if (!RTC) return Promise.resolve(snapshot(LAN_IP_UNAVAILABLE, [], false));

    pendingDetection = new Promise((resolve) => {
        const addresses = new Set();
        let mdnsMasked = false;
        let connection;
        let channel;
        let timer;
        let finished = false;

        const consume = (candidate) => {
            const parsed = parseIceCandidate(candidate);
            if (!parsed) return;
            if (parsed.mdnsMasked) {
                mdnsMasked = true;
            } else if (isPrivateAddress(parsed.address)) {
                addresses.add(parsed.address);
            }
        };
        const consumeLocalDescription = () => {
            const sdp = connection && connection.localDescription && connection.localDescription.sdp;
            String(sdp || '').split(/\r?\n/).forEach((line) => {
                if (line.indexOf('a=candidate:') === 0) consume(line.slice(2));
            });
        };
        const finish = () => {
            if (finished) return;
            finished = true;
            clearTimeout(timer);
            consumeLocalDescription();
            try { if (channel) channel.close(); } catch (_) { }
            try { if (connection) connection.close(); } catch (_) { }
            const status = addresses.size > 0 ? 'available' : (mdnsMasked ? LAN_IP_MASKED : LAN_IP_UNAVAILABLE);
            resolve(snapshot(status, addresses, mdnsMasked));
        };

        try {
            // Host candidates do not need a public STUN service. Avoiding one also
            // prevents this local-address feature from leaking browsing activity.
            connection = new RTC({ iceServers: [] });
            connection.onicecandidate = (event) => {
                if (event.candidate) consume(event.candidate);
                else finish();
            };
            connection.onicecandidateerror = () => { };
            channel = connection.createDataChannel('synap-local-address');
            timer = setTimeout(finish, Math.max(250, Math.min(Number(timeout) || 1200, 3000)));
            connection.createOffer()
                .then((offer) => connection.setLocalDescription(offer))
                .catch(finish);
        } catch (_) {
            finish();
        }
    }).finally(() => {
        pendingDetection = null;
    });
    return pendingDetection;
}

export function getLanIpState() {
    return { ...lanIpState, addresses: lanIpState.addresses.slice() };
}

export default {
    /**
     * 网络类型
     * @returns 网络类型
     */
    type: function () {
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        if (!connection) return 'unknown';
        if (connection.type === 'wifi') return 'Wi-Fi';
        if (connection.effectiveType) return String(connection.effectiveType).toUpperCase();
        return connection.type || 'other';
    },
    /**
     * 网络信息
     * @returns 网络信息
     */
    info: function () {
        let info = 'unknown|unknown|unknown|unknown';
        let effectiveType = 'unknown';
        let downlinkMax = 'unknown';
        const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        if (conn) {
            effectiveType = conn.type || conn.effectiveType || 'unknown';
            downlinkMax = conn.downlinkMax || conn.downlink || 'unknown';
            info = [effectiveType, downlinkMax, conn.rtt, conn.saveData].join('|');
        }
        return info;
    },
    lanIp: function () {
        if (lanIpState.addresses.length) return lanIpState.addresses.join(',');
        if (lanIpState.status === 'uninitialized') return LAN_IP_UNAVAILABLE;
        return lanIpState.status;
    },
    detectLanIp,
    getLanIpState
};
