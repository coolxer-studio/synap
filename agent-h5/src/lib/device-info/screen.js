export default {
    /**
     * 获取屏幕计算值
     * @returns 
     */
    screenSize: function () {
        const setScreenDom = function () {
            const temDom = document.createElement('div');
            temDom.style.width = '1cm';
            temDom.setAttribute("id", "new-_date");
            document.body.appendChild(temDom);
            let ww = document.getElementById("new-_date"), w = screen.width / ww.offsetWidth,
                h = screen.height / ww.offsetWidth, r = Math.round(Math.sqrt(w * w + h * h) / 2.54, 4);
            try {
                ww.remove();
            } catch (e) {
                ww = null;
            }
            return r
        }
        try {
            if (!document.body) return 0;
            const result = setScreenDom();
            return result === Infinity || !Number.isFinite(result) ? 0 : result;
        } catch (e) {
            return 0
        }
    }

}
