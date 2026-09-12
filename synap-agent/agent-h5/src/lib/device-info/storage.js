const disk = { status: 'pending', value: '', error: '' };

async function prepare(timeout = 1000) {
  if (!navigator.storage || typeof navigator.storage.estimate !== 'function') {
    disk.status = 'unavailable';
    return disk;
  }
  try {
    const estimate = await Promise.race([
      navigator.storage.estimate(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), timeout))
    ]);
    disk.value = Number.isFinite(Number(estimate && estimate.quota)) ? String(estimate.quota) : '';
    disk.status = disk.value ? 'available' : 'unavailable';
  } catch (error) {
    disk.status = error && error.message === 'timeout' ? 'blocked' : 'error';
    disk.error = String(error && error.name || 'storage-error');
  }
  return disk;
}

export default {
  prepare,
  totalRam() { return String(navigator.deviceMemory || -1); },
  totalDisk() { return disk.value; },
  capability() { return { ...disk, value: undefined }; }
};
