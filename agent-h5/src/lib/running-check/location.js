const state = {
  time: 0,
  speed: 0,
  latitude: 0,
  longitude: 0,
  country: '',
  province: '',
  city: '',
  county: '',
  thoroughfare: ''
};

let requestToken = 0;

function update(position) {
  state.time = position.timestamp || Date.now();
  state.speed = Number(position.coords.speed) || 0;
  state.latitude = Number(position.coords.latitude) || 0;
  state.longitude = Number(position.coords.longitude) || 0;
}

const locationInfo = {
  start() {
    const token = ++requestToken;
    if (!navigator.geolocation) return function () {};
    try {
      navigator.geolocation.getCurrentPosition(
        (position) => { if (token === requestToken) update(position); },
        () => {},
        { maximumAge: 300000, timeout: 10000, enableHighAccuracy: false }
      );
    } catch (_) { }
    return function cleanup() { requestToken += 1; };
  },
  getLocation() {
    return { ...state };
  },
  getLbs() {
    return { lat: String(state.latitude || ''), lon: String(state.longitude || '') };
  }
};

Object.keys(state).forEach((key) => {
  locationInfo[key] = function () { return state[key]; };
});

export default locationInfo;
