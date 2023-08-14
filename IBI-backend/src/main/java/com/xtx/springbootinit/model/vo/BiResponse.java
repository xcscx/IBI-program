package com.xtx.springbootinit.model.vo;

import lombok.Data;

/**
 * Bi 返回结果
 */
@Data
public class BiResponse {

    /**
     * 返回图表代码
     */
    private String genChart;

    /**
     * 返回分析结论
     */
    private String genResult;

    /**
     * 图表对象Id
     */
    private Long chartId;
}
