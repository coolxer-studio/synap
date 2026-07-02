window.ct_error_config = {
    "message": [".*"]
}

var data = {

}

export default {

    monitor: function (msgCallback) {
        let errorMsg = {
            type: 'ErrorData',
            from: '',
            message: '',
            source: '',
            lineno: '',
            colno: ''
        }

        var errorMsgCallback = function (errorMsg) {
            window.ct_error_config['message'].forEach(regString => {
                var pattern = new RegExp(regString);
                if (pattern.test(errorMsg.message)) {
                    msgCallback(errorMsg);
                }
            });
        }


        // onerror 捕获未知异常
        if (!window.originalOnerror) {
            // 防治多次初始化，保存原始方法
            if (window.onerror) {
                window.originalOnerror = window.onerror;
            } else {
                window.originalOnerror = "window.onerror not exist";
            }
            // 设置错误处理函数
            window.onerror = function (message, source, lineno, colno, error) {
                // 封装错误信息返回
                errorMsg.from = 'windowOnerror';
                errorMsg.message = message;
                errorMsg.source = source;
                errorMsg.lineno = lineno;
                errorMsg.colno = colno;
                errorMsgCallback(errorMsg);
                if (typeof (window.originalOnerror) === 'function') {
                    // 执行原始方法
                    return window.originalOnerror(message, source, lineno, colno, error);
                } else {
                    // 放行浏览器默认的错误处理
                    return false;
                }
            };
        }

        // console.error获取自定义输出的error信息
        if (!window.originalConsoleError) {
            window.originalConsoleError = window.console.error;
            window.console.error = function (message) {
                // 封装错误信息返回
                errorMsg.from = 'consoleError';
                errorMsg.message = message;
                errorMsgCallback(errorMsg);
                if (typeof (window.originalConsoleError) === 'function') {
                    // 执行原始方法
                    window.originalConsoleError(message);
                }
            };
        }
    },
    loadConfig: function (config) {
        window.ct_error_config = config['errorConfig'];
    }

}