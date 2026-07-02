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
            if (document.readyState === 'complete') {
                return setScreenDom()
            } else {
                if (document.addEventListener) {
                    document.addEventListener('DOMContentLoaded', function () {
                        return setScreenDom()
                    }, false);
                } else if (document.attachEvent) {
                    document.attachEvent('onreadystatechange', function () {
                        if (document.readyState === 'complete') {
                            return setScreenDom()
                        }
                    });
                }
            }
            return r === Infinity ? 0 : r
        } catch (e) {
            return 0
        }
    }

}