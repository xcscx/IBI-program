package com.xtx.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.xtx.springbootinit.annotation.AuthCheck;
import com.xtx.springbootinit.bizmq.MyMessageProducer;
import com.xtx.springbootinit.common.BaseResponse;
import com.xtx.springbootinit.common.DeleteRequest;
import com.xtx.springbootinit.common.ErrorCode;
import com.xtx.springbootinit.common.ResultUtils;
import com.xtx.springbootinit.constant.BiMqConstant;
import com.xtx.springbootinit.constant.CommonConstant;
import com.xtx.springbootinit.constant.UserConstant;
import com.xtx.springbootinit.exception.BusinessException;
import com.xtx.springbootinit.exception.ThrowUtils;
import com.xtx.springbootinit.manager.AiManager;
import com.xtx.springbootinit.model.dto.chart.*;
import com.xtx.springbootinit.model.entity.Chart;
import com.xtx.springbootinit.model.entity.User;
import com.xtx.springbootinit.model.vo.BiResponse;
import com.xtx.springbootinit.service.ChartService;
import com.xtx.springbootinit.service.UserService;
import com.xtx.springbootinit.utils.ExcelUtils;
import com.xtx.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * 图表接口
 *
 * @author IT蛋
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private MyMessageProducer myMessageProducer;


    /**
     * 智能分析
     *
     * @param multipartFile
     * @param genChartByAIRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<BiResponse> genChartByAI(@RequestPart("file") MultipartFile multipartFile,
                                             GenChartByAIRequest genChartByAIRequest, HttpServletRequest request) {
        final long MAX_MB = 1024 * 1024L;
        final List<String> validFileSuffix = Arrays.asList("xls", "xlsx");

        String name = genChartByAIRequest.getName();
        String goal = genChartByAIRequest.getGoal();
        String chartType = genChartByAIRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件信息(文件大小， 文件后缀)
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        ThrowUtils.throwIf(size > MAX_MB, ErrorCode.PARAMS_ERROR, "不支持传输文件超过1MB");

        String suffer = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!validFileSuffix.contains(suffer), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        // 要求登录才能使用
        User loginUser = userService.getLoginUser(request);

        // 无需写 prompt
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "根据这两部分内容，按照以下指定格式生成内容（除此之外不需要任何额外多余开头，结尾，注释等内容）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成多余内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论，越详细越好，不要生成多余内容，比如注释}";



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
        String csvData =  ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        // TODO: 设置状态枚举值
        chart.setStatus("wait");
        // TODO：分库存储图表信息数据
        chart.setChartData(csvData);
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "数据库保存异常");

        // TODO: 建议处理任务队列满了后，抛异常的情况
        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为“执行中”，等执行完成后，修改为“已完成”，保存执行结果；执行失败后，状态改为“失败”，记录任务失败信息
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if(!b) {
                chartService.handleChartUpdateError(chart.getId(), "图表创建更改失败");
                return;
            }

            // 调用AI
            String result = aiManager.doChat(BiMqConstant.BI_MODEL_ID, userInput.toString());
            String[] splits = result.split("【【【【【");
            if(splits.length < 3) {
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
                chartService.handleChartUpdateError(chart.getId(), "更新图表信息状态失败");
            }

        }, threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

//        // 读取到用户上传的excel 文件，进行一个处理
//        User loginUser = userService.getLoginUser(request);
//        // 文件目录：根据业务、用户来划分
//        String uuid = RandomStringUtils.randomAlphanumeric(8);
//        String filename = uuid + "-" + multipartFile.getOriginalFilename();
//        File file = null;
//        try {
//            // 上传文件
//
//            // 返回可访问地址
//            return ResultUtils.success("成功返回可编译地址");
////            return ResultUtils.success(FileConstant.COS_HOST + filepath);
//        } catch (Exception e) {
////            log.error("file upload error, filepath = " + filepath, e);
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
//        } finally {
//            if (file != null) {
//                // 删除临时文件
//                boolean delete = file.delete();
//                if (!delete) {
////                    log.error("file delete error, filepath = {}", filepath);
//                }
//            }
//        }
    }


    /**
     * 智能分析-mq
     *
     * @param multipartFile
     * @param genChartByAIRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/mq")
    public BaseResponse<BiResponse> genChartByAIUseMQ(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAIRequest genChartByAIRequest, HttpServletRequest request) {
        final long MAX_MB = 1024 * 1024L;
        final List<String> validFileSuffix = Arrays.asList("xls", "xlsx");

        String name = genChartByAIRequest.getName();
        String goal = genChartByAIRequest.getGoal();
        String chartType = genChartByAIRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件信息(文件大小， 文件后缀)
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        ThrowUtils.throwIf(size > MAX_MB, ErrorCode.PARAMS_ERROR, "不支持传输文件超过1MB");

        String suffer = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!validFileSuffix.contains(suffer), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        // 要求登录才能使用
        User loginUser = userService.getLoginUser(request);


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
        String csvData =  ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        // TODO: 设置状态枚举值
        chart.setStatus("wait");
        // TODO：分库存储图表信息数据
        chart.setChartData(csvData);
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "数据库保存异常");
        long newChartId = chart.getId();
        // TODO: 建议处理任务队列满了后，抛异常的情况
        myMessageProducer.sendMessage(String.valueOf(newChartId));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(newChartId);
        return ResultUtils.success(biResponse);
    }


    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    /**
     * 查询包装类
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if(chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq( "isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
