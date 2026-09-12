const state = {
  timestamp: 0,
  speed: 0,
  latitude: 0,
  longitude: 0,
  accuracy: 0,
  altitude: null,
  heading: null,
  permissionState: 'unknown',
  status: 'disabled',
  country: '',
  province: '',
  city: '',
  county: '',
  thoroughfare: ''
};

let requestToken = 0;

function update(position) {
  state.timestamp = position.timestamp || Date.now();
  state.speed = Number(position.coords.speed) || 0;
  state.latitude = Number(position.coords.latitude) || 0;
  state.longitude = Number(position.coords.longitude) || 0;
  state.accuracy = Number(position.coords.accuracy) || 0;
  state.altitude = Number.isFinite(Number(position.coords.altitude)) ? Number(position.coords.altitude) : null;
  state.heading = Number.isFinite(Number(position.coords.heading)) ? Number(position.coords.heading) : null;
  state.status = 'available';
}

const locationInfo = {
  start() {
    const token = ++requestToken;
    if (!navigator.geolocation) {
      state.status = 'unavailable';
      return function () {};
    }
    state.status = 'pending';
    try {
      if (navigator.permissions && typeof navigator.permissions.query === 'function') {
        navigator.permissions.query({ name: 'geolocation' }).then((permission) => {
          if (token === requestToken) state.permissionState = permission.state || 'unknown';
        }).catch(() => {});
      }
    } catch (_) { }
    try {
      navigator.geolocation.getCurrentPosition(
        (position) => { if (token === requestToken) update(position); },
        (error) => {
          if (token !== requestToken) return;
          state.status = error && error.code === 1 ? 'blocked' : 'error';
          if (error && error.code === 1) state.permissionState = 'denied';
        },
        { maximumAge: 300000, timeout: 10000, enableHighAccuracy: false }
      );
    } catch (_) { state.status = 'error'; }
    return function cleanup() { requestToken += 1; };
  },
  getLocation() {
    return { ...state };
  },
  getLbs() {
    return { lat: String(state.latitude || ''), lon: String(state.longitude || '') };
  }
};

locationInfo.time = function () { return state.timestamp; };

Object.keys(state).forEach((key) => {
  locationInfo[key] = function () { return state[key]; };
});

export default locationInfo;
