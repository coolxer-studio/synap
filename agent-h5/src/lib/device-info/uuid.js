const { v4: uuidv4 } = require('uuid');

export default {

    /**
     * 封装一个方法，用来查询并生成 UUID
     * @returns UUID
     */
    guid: function () {
        const storedGuid = localStorage.getItem('_g_d'); // 从本地存储获取 GUID
        if (storedGuid) {
            // 如果本地已经存储了 GUID，直接返回
            return storedGuid;
        } else {
            // 如果本地没有存储 GUID，则生成新的 UUID
            const newGuid = uuidv4();
            localStorage.setItem('_g_d', newGuid); // 将新生成的 GUID 存储到本地
            return newGuid;
        }
    }
}