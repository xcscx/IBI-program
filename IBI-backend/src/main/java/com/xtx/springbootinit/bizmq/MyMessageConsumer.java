package com.xtx.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.xtx.springbootinit.common.ErrorCode;
import com.xtx.springbootinit.constant.BiMqConstant;
import com.xtx.springbootinit.exception.BusinessException;
import com.xtx.springbootinit.manager.AiManager;
import com.xtx.springbootinit.model.entity.Chart;
import com.xtx.springbootinit.service.ChartService;
import com.xtx.springbootinit.utils.ExcelUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;


@Component
@Slf4j
public class MyMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;

    // 指定程序监听的消息队列和确认机制
    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if(StringUtils.isBlank(message)) {
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if(chart == null) {
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图表为空");
        }
        // 先修改图表任务状态为“执行中”，等执行完成后，修改为“已完成”，保存执行结果；执行失败后，状态改为“失败”，记录任务失败信息
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean b = chartService.updateById(updateChart);
        if(!b) {
            channel.basicNack(deliveryTag, false, false);
            chartService.handleChartUpdateError(chart.getId(), "图表创建更改失败");
            return;
        }

        // 调用AI
        String result = aiManager.doChat(BiMqConstant.BI_MODEL_ID, buildUserInput(chart));
        String[] splits = result.split("【【【【【");
        if(splits.length < 3) {
            channel.basicNack(deliveryTag, false, false);
            chartService.handleChartUpdateError(chart.getId(), "AI 生成错误");
            return;
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chart.getId());
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);
        // TODO： 定义状态为枚举值
        updateChartResult.setStatus("succeed");
        boolean updateResult = chartService.updateById(updateChartResult);
        if(!updateResult) {
            channel.basicNack(deliveryTag, false, false);
            chartService.handleChartUpdateError(chart.getId(), "更新图表信息状态失败");
        }
        // 消息确认
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 构建用户输入
     * @param chart
     * @return
     */
    private String buildUserInput(Chart chart) {
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用" + chartType;
        }

        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        //获得压缩数据
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }


}
