import enc from './secure-communication/encrypt-decrypt'
import cookieOperate from "./cookie-operate";
// 存储逻辑

const readF = (key, value)=> {
    // const value = window.esl[key] !== undefined ? window.esl[key] : null;
    if (value) {
        const item = JSON.parse(value);
        if (item.expiredAt && +item.expiredAt >= +Date.now()) {
            return key !== 'h_five_event' ? JSON.parse(enc.Decrypt(item.value,enc.k(window.kk))) : item.value;
        }
    }
    return null;
}

const setF = (key, e, expire) => {
    return JSON.stringify({
        expiredAt: +Date.now() + (!expire ? 2592e8 : expire),
        value: key !== 'h_five_event' ? enc.Encrypt(JSON.stringify(e), enc.k(window.kk)) : e
    })
}


const agLocalStore = function () {
    function AgLocalStore() {
    }

    return AgLocalStore.get = function (key) {
        try {
            try {
                // return window.localStorage ? this._readFromLocalStorage(key) : this._readFromCookie(key);

                return window.localStorage ? this._readFromLocalStorage(key) : this._readFromCookie(key);
            } catch (v) {
                if (navigator.cookieEnabled) {
                    return this._readFromCookie(key);
                } else {
                    return this._readFromMemory(key);
                }
            }
        } catch (exception) {
            return null;
        }
    }, AgLocalStore.set = function (key, e, expire) {
        if (null == expire) expire = 864e5;
        try {
            try {
                // return window.localStorage ? this._setInLocalStorage(key, e, expire) : this._setInCookie(key, e, expire / 1e3);
                return window.localStorage ? this._setInLocalStorage(key, e, expire) : this._setInCookie(key, e, expire / 1e3);
            } catch (v) {
                if (navigator.cookieEnabled) {
                    return this._setInCookie(key, e, expire / 1e3);
                } else {
                    return this._setInMemory(key, e, expire / 1e3)
                }

            }
        } catch (exception) {
            console.error(exception)
            return null;
        }
    }, AgLocalStore._readFromMemory = function (key) {
        if (window.esl){
            const value = window.esl[key] !== undefined ? window.esl[key] : null;
            return readF(key, value)

        } else {
            return null;
        }
    }, AgLocalStore._setInMemory = function (key, e, expire) {
        if (window.esl){
            return window.esl[key] = setF(key, e, expire);
        } else {
            window.esl = {}
            return window.esl[key] = setF(key, e, expire);
        }
    }, AgLocalStore._readFromCookie = function (key) {
        const value = cookieOperate.getItem(key)
        return readF(key,value);
    }, AgLocalStore._setInCookie = function (key, e, expire) {
        return cookieOperate.setItem(key, setF(key, e, expire), expire, "/", [window.location.hostname]);
    }, AgLocalStore._readFromLocalStorage = function (key) {
        const value = window.localStorage.getItem(key);
        return readF(key, value);
    }, AgLocalStore._setInLocalStorage = function (key, e, expire) {
        if (key === '__rk') window.sessionStorage.setItem(key.split('').reverse().join('-'), e)
        return window.localStorage.setItem(key, setF(key, e, expire));
    }, AgLocalStore.keys = function () {
        if (window.localStorage) {
            const keys = [];
            for (let i = 0; i < window.localStorage.length; i++)
                keys.push(window.localStorage.key(i));
            return keys;
        } else {
            return cookieOperate.keys;
        }
    }, AgLocalStore.hasItem = function (itemKey) {
        try {
            window.localStorage.setItem('_-_-_-_', '1');
            window.localStorage.removeItem('_-_-_-_');
            const keys = AgLocalStore.keys();
            let has = false;
            for (let i = 0; i < keys.length; i++) {
                if (keys[i] === itemKey) {
                    has = true;
                    break;
                }
            }
            return has;
        } catch (e) {
            if (navigator.cookieEnabled) {
                return cookieOperate.hasItem(itemKey);
            } else {
                if (window.esl) {
                    return window.esl.hasOwnProperty(itemKey)
                }
                return false
            }

        }
        // if (window.localStorage) {
        //     const keys = AgLocalStore.keys();
        //     let has = false;
        //     for (let i = 0; i < keys.length; i++) {
        //         if (keys[i] === itemKey) {
        //             has = true;
        //             break;
        //         }
        //     }
        //     return has;
        //     // return h_five_cookie.hasItem(itemKey);
        // } else {
        //     return h_five_cookie.hasItem(itemKey);
        // }
    }, AgLocalStore.remove = function (itemKey) {
        return window.localStorage ? window.localStorage.removeItem(itemKey) : cookieOperate.removeItem(itemKey);
    }, AgLocalStore.clear = function () {
        const keys = this.keys();
        if (keys) {
            const that = this;
            keys.forEach(function (itemKey) {
                that.remove(itemKey);
            });
        }
    }, AgLocalStore;
}();

export default agLocalStore