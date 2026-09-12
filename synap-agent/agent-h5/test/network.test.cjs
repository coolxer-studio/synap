const test = require('node:test');
const assert = require('node:assert/strict');

global.window = {};
global.navigator = {};

const { isPrivateAddress, parseIceCandidate, detectLanIp } = require('../src/lib/device-info/network');

test('classifies private IPv4 and IPv6 addresses without accepting public addresses', () => {
  assert.equal(isPrivateAddress('10.2.3.4'), true);
  assert.equal(isPrivateAddress('172.31.2.3'), true);
  assert.equal(isPrivateAddress('192.168.1.20'), true);
  assert.equal(isPrivateAddress('fd12:3456::1'), true);
  assert.equal(isPrivateAddress('8.8.8.8'), false);
  assert.equal(isPrivateAddress('172.32.2.3'), false);
});

test('parses host candidates and identifies mDNS masking', () => {
  assert.deepEqual(
    parseIceCandidate('candidate:1 1 UDP 2122260223 192.168.10.5 54321 typ host generation 0'),
    { address: '192.168.10.5', mdnsMasked: false }
  );
  assert.deepEqual(
    parseIceCandidate('candidate:2 1 UDP 2122260223 masked-host.local 54322 typ host generation 0'),
    { address: '', mdnsMasked: true }
  );
  assert.equal(parseIceCandidate('candidate:3 1 UDP 1 203.0.113.1 40000 typ srflx'), null);
});

test('collects private host candidates through the asynchronous WebRTC path', async () => {
  class FakePeerConnection {
    constructor() {
      this.localDescription = { sdp: '' };
    }
    createDataChannel() {
      return { close() {} };
    }
    createOffer() {
      return Promise.resolve({ type: 'offer', sdp: '' });
    }
    setLocalDescription() {
      Promise.resolve().then(() => {
        this.onicecandidate({
          candidate: {
            candidate: 'candidate:1 1 UDP 1 192.168.50.8 50000 typ host',
            type: 'host',
            address: '192.168.50.8'
          }
        });
        this.onicecandidate({ candidate: null });
      });
      return Promise.resolve();
    }
    close() {}
  }
  window.RTCPeerConnection = FakePeerConnection;
  const result = await detectLanIp();
  assert.equal(result.status, 'available');
  assert.deepEqual(result.addresses, ['192.168.50.8']);
});
