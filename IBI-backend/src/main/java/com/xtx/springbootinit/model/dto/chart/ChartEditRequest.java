package com.xtx.springbootinit.model.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑请求
 *
 * @author IT蛋
 */
@Data
public class ChartEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图表名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图标数据
     */
    private String chartData;

    /**
     * 图标类型
     */
    private String chartType;

    private static final long serialVersionUID = 1L;
}