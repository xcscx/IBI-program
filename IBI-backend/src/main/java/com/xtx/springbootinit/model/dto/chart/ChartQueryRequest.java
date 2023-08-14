package com.xtx.springbootinit.model.dto.chart;

import com.xtx.springbootinit.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询请求
 *
 * @author IT蛋
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ChartQueryRequest extends PageRequest implements Serializable {

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
     * 图标类型
     */
    private String chartType;

    /**
     * 创建用户Id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}