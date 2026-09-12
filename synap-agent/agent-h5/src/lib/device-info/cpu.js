export default{
    /**
     * cpu核心
     * @returns 
     */
    totalCore: function () {
        if (navigator.hardwareConcurrency) {
          return '' + navigator.hardwareConcurrency
        }
        return '-1'
      },
}