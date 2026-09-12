const state = { status: 'pending', value: '', error: '' };

function serialize(battery) {
  return [battery.charging, battery.chargingTime, battery.dischargingTime, battery.level].join('|');
}

async function prepare(timeout = 1000) {
  const battery = navigator.battery;
  if (battery) {
    state.value = serialize(battery);
    state.status = 'available';
    return state;
  }
  if (typeof navigator.getBattery !== 'function') {
    state.status = 'unavailable';
    return state;
  }
  try {
    const battery = await Promise.race([
      navigator.getBattery(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), timeout))
    ]);
    state.value = serialize(battery);
    state.status = 'available';
  } catch (error) {
    state.status = error && error.message === 'timeout' ? 'blocked' : 'error';
    state.error = String(error && error.name || 'battery-error');
  }
  return state;
}

export default {
  prepare,
  info() { return state.value; },
  capability() { return { ...state, value: undefined }; }
};
