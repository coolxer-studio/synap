export default {
    /**
     * 电池信息
     * @returns 
     */
    info: function () {
        window.batteryInfo = '';
        if (navigator.battery) {
            const {
                charging,
                chargingTime,
                dischargingTime,
                level
            } = navigator['connection'];
            window.batteryInfo = [charging, chargingTime, dischargingTime, level].join('|');

        } else if (typeof navigator.getBattery === 'function') {
            navigator.getBattery().then((f) => {
                const {
                    charging,
                    chargingTime,
                    dischargingTime,
                    level
                } = f
                window.batteryInfo = [charging, chargingTime, dischargingTime, level].join('|');
            })
        }
        return window.batteryInfo;
    }
}