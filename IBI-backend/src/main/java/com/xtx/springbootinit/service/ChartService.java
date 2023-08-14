package com.xtx.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xtx.springbootinit.model.dto.chart.ChartQueryRequest;
import com.xtx.springbootinit.model.entity.Chart;


/**
 *
 */
public interface ChartService extends IService<Chart> {

    /**
     * 获取查询条件
     *
     * @param chartQueryRequest
     * @return
     */
    QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);

    void handleChartUpdateError(long chartId, String execMessage);


}
