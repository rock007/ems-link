package com.zheng.ems_link.cloudmqtt;

public enum HistoryErrorCode {
    INVALID_REQUEST("请求格式不符合约定"),
    INVALID_TYPE("设备大类非法、未知或不属于该项目"),
    DEVICE_MISMATCH("设备 ID 不属于所选设备大类或项目"),
    INVALID_KEY("测点编码非法、重复、未知或不属于所选设备大类"),
    AMBIGUOUS_KEY("测点编码在所选设备大类下无法唯一定位"),
    TOO_MANY_KEYS("query.keys 超过 5 个"),
    INVALID_TIME_RANGE("时间格式或范围无效，或跨度超过一个月"),
    RESULT_COUNT_EXCEEDED("同一次查询的测点值数量超过配置的返回上限"),
    QUERY_TIMEOUT("查询超时"),
    DATA_UNAVAILABLE("历史数据暂不可用"),
    INTERNAL_ERROR("其他服务错误");

    private final String message;

    HistoryErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
