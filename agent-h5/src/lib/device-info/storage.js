export default {
    /**
     * 内存
     * @returns 
     */
    totalRam: function () {
        return '' + (navigator.deviceMemory || -1)
    },
    /**
     * 磁盘空间
     */
    totalDisk: function () {
        if (navigator.storage && navigator.storage.estimate) {
            navigator.storage.estimate().then(function (e) {
                window.totalDisk = e.quota;
            });
        }
    }
}