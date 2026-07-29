
function generateUUID() {
  let d = new Date().getTime();
  let uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    let r = (d + Math.random() * 16) % 16 | 0;
    d = Math.floor(d / 16);
    return (c === 'x'? r : (r & 0x3 | 0x8)).toString(16);
  });
  return uuid;
}

export default {

    /**
     * 封装一个方法，用来查询并生成 UUID
     * @returns UUID
     */
    guid: function () {
        const storedGuid = wx.getStorageSync('_g_d'); // 从本地存储获取 GUID
        if (storedGuid) {
            // 如果本地已经存储了 GUID，直接返回
            return storedGuid;
        } else {
            // 如果本地没有存储 GUID，则生成新的 UUID
            const newGuid = generateUUID();
            wx.setStorageSync('_g_d', newGuid); // 将新生成的 GUID 存储到本地
            return newGuid;
        }
    }
}