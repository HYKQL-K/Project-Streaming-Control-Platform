package com.laigeoffer.pmhub.project.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.laigeoffer.pmhub.api.system.UserFeignService;
import com.laigeoffer.pmhub.api.system.domain.dto.SysUserDTO;
import com.laigeoffer.pmhub.api.workflow.DeployFeignService;
import com.laigeoffer.pmhub.base.core.config.PmhubConfig;
import com.laigeoffer.pmhub.base.core.constant.SecurityConstants;
import com.laigeoffer.pmhub.base.core.core.domain.R;
import com.laigeoffer.pmhub.base.core.core.domain.dto.ApprovalSetDTO;
import com.laigeoffer.pmhub.base.core.core.domain.entity.SysUser;
import com.laigeoffer.pmhub.base.core.core.domain.model.LoginUser;
import com.laigeoffer.pmhub.base.core.core.domain.vo.SysUserVO;
import com.laigeoffer.pmhub.base.core.enums.LogTypeEnum;
import com.laigeoffer.pmhub.base.core.enums.ProjectStatusEnum;
import com.laigeoffer.pmhub.base.core.enums.ProjectTaskPriorityEnum;
import com.laigeoffer.pmhub.base.core.enums.ProjectTaskStatusEnum;
import com.laigeoffer.pmhub.base.core.exception.ServiceException;
import com.laigeoffer.pmhub.base.core.utils.DateUtils;
import com.laigeoffer.pmhub.base.core.utils.file.FileUtils;
import com.laigeoffer.pmhub.base.security.utils.SecurityUtils;
import com.laigeoffer.pmhub.project.domain.*;
import com.laigeoffer.pmhub.project.domain.vo.project.ProjectVO;
import com.laigeoffer.pmhub.project.domain.vo.project.log.*;
import com.laigeoffer.pmhub.project.domain.vo.project.member.ProjectMemberResVO;
import com.laigeoffer.pmhub.project.domain.vo.project.task.*;
import com.laigeoffer.pmhub.project.mapper.*;
import com.laigeoffer.pmhub.project.service.ProjectLogService;
import com.laigeoffer.pmhub.project.service.ProjectTaskService;
import com.laigeoffer.pmhub.project.service.task.QueryTaskLogFactory;
import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.ServerException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author canghe
 * @date 2022-12-14 15:00
 */
@Service
@Slf4j
public class ProjectTaskServiceImpl extends ServiceImpl<ProjectTaskMapper, ProjectTask> implements ProjectTaskService {
    @Autowired
    private ProjectTaskMapper projectTaskMapper;
    @Autowired
    private ProjectMemberMapper projectMemberMapper;
    @Autowired
    private ProjectLogService projectLogService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ProjectStageMapper projectStageMapper;
    @Autowired
    private QueryTaskLogFactory queryTaskLogFactory;
    @Autowired
    private ProjectFileMapper projectFileMapper;
    @Autowired
    private ProjectTaskProcessMapper projectTaskProcessMapper;

    // 远程调用流程服务
    @Resource
    private DeployFeignService wfDeployService;

    // 远程调用用户服务
    @Resource
    private UserFeignService userFeignService;

    @Override
    public Long queryTodayTaskNum() {
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(ProjectTask::getBeginTime, DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD_HH_MM_SS).format(LocalDateTime.now().with(LocalTime.MIN))
                , DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD_HH_MM_SS).format(LocalDateTime.now().with(LocalTime.MAX))).eq(ProjectTask::getDeleted, 0);
        if (projectTaskMapper.selectCount(queryWrapper) == null) {
            return 0L;
        }
        return projectTaskMapper.selectCount(queryWrapper);

    }

    @Override
    public Long queryOverdueTaskNum() {
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(ProjectTask::getCloseTime, DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD_HH_MM_SS).format(LocalDateTime.now())).eq(ProjectTask::getDeleted, 0)
                .ne(ProjectTask::getExecuteStatus, ProjectTaskStatusEnum.FINISHED.getStatus());
        if (projectTaskMapper.selectCount(queryWrapper) == null) {
            return 0L;
        }
        return projectTaskMapper.selectCount(queryWrapper);
    }

    @Override
    public List<TaskStatisticsVO> queryTaskStatisticsList() {
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectTask::getDeleted, 0);
        List<TaskStatisticsVO> taskStatisticsVOList = new ArrayList<>(10);
        List<ProjectTask> list = projectTaskMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            for (ProjectTaskStatusEnum value : ProjectTaskStatusEnum.values()) {
                TaskStatisticsVO taskStatisticsVO = new TaskStatisticsVO();
                taskStatisticsVO.setStatus(value.getStatus());
                taskStatisticsVO.setStatusName(value.getStatusName());
                taskStatisticsVO.setTaskNum(0);
                taskStatisticsVOList.add(taskStatisticsVO);
            }
            return taskStatisticsVOList;
        } else {
            // 待认领
            TaskStatisticsVO noClaim = new TaskStatisticsVO();
            noClaim.setStatus(ProjectTaskStatusEnum.NO_CLAIMED.getStatus());
            noClaim.setStatusName(ProjectTaskStatusEnum.NO_CLAIMED.getStatusName());
            noClaim.setTaskNum((int) list.stream().filter(a -> a.getUserId() == null).count());
            taskStatisticsVOList.add(noClaim);
            // 进行中
            List<ProjectTask> doingList = list.stream().filter(a -> ProjectTaskStatusEnum.DOING.getStatus().equals(a.getStatus())).collect(Collectors.toList());
            TaskStatisticsVO doing = new TaskStatisticsVO();
            doing.setStatus(ProjectTaskStatusEnum.DOING.getStatus());
            doing.setStatusName(ProjectTaskStatusEnum.DOING.getStatusName());
            doing.setTaskNum(doingList.size());
            taskStatisticsVOList.add(doing);
            // 已完成
            List<ProjectTask> finishList = list.stream().filter(a -> ProjectTaskStatusEnum.FINISHED.getStatus().equals(a.getStatus())).collect(Collectors.toList());
            TaskStatisticsVO finish = new TaskStatisticsVO();
            finish.setStatus(ProjectTaskStatusEnum.FINISHED.getStatus());
            finish.setStatusName(ProjectTaskStatusEnum.FINISHED.getStatusName());
            finish.setTaskNum(finishList.size());
            taskStatisticsVOList.add(finish);
            // 已逾期
            List<ProjectTask> overdueList = list.stream().filter(a -> a.getCloseTime() != null && a.getCloseTime().getTime() < new Date().getTime()).collect(Collectors.toList());
            TaskStatisticsVO overdue = new TaskStatisticsVO();
            overdue.setStatus(ProjectTaskStatusEnum.OVERDUE.getStatus());
            overdue.setStatusName(ProjectTaskStatusEnum.OVERDUE.getStatusName());
            overdue.setTaskNum(overdueList.size());
            taskStatisticsVOList.add(overdue);
        }

        return taskStatisticsVOList;
    }

    @Override
    public PageInfo<TaskResVO> queryMyTaskList(TaskReqVO taskReqVO) {
        PageInfo<TaskResVO> pageInfo;
        PageHelper.startPage(taskReqVO.getPageNum(), taskReqVO.getPageSize());
        switch (taskReqVO.getType()) {
            // 我执行的
            case 1:
                pageInfo = new PageInfo<>(projectTaskMapper.queryMyExecutedTaskList(taskReqVO.getProjectId(), SecurityUtils.getUserId()));
                if (CollectionUtils.isNotEmpty(pageInfo.getList())) {
                    pageInfo.getList().forEach(a -> a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus())));
                }
                return pageInfo;
            // 我参与的
            case 2:
                pageInfo = new PageInfo<>(projectTaskMapper.queryMyPartookTaskList(taskReqVO.getProjectId(), SecurityUtils.getUserId()));
                if (CollectionUtils.isNotEmpty(pageInfo.getList())) {
                    pageInfo.getList().forEach(a -> a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus())));
                }
                return pageInfo;
            // 我创建的
            case 3:
                pageInfo = new PageInfo<>(projectTaskMapper.queryMyCreatedTaskList(taskReqVO.getProjectId(), SecurityUtils.getUsername()));
                if (CollectionUtils.isNotEmpty(pageInfo.getList())) {
                    pageInfo.getList().forEach(a -> a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus())));
                }
                return pageInfo;
        }
        return new PageInfo<>();
    }

    @Override
    public TaskStatusStatsVO queryTaskStatusStats(ProjectVO projectVO) {
        TaskStatusStatsVO taskStatusStatsVO = new TaskStatusStatsVO();
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectTask::getProjectId, projectVO.getProjectId());
        queryWrapper.eq(ProjectTask::getDeleted, 0);
        List<ProjectTask> projectTasks = projectTaskMapper.selectList(queryWrapper);
        if (CollectionUtils.isNotEmpty(projectTasks)) {
            taskStatusStatsVO.setTotal(projectTasks.size());
            taskStatusStatsVO.setToBeAssign((int) projectTasks.stream().filter(a -> a.getUserId() == null).count());
            taskStatusStatsVO.setUnDone((int) projectTasks.stream().filter(a -> !Objects.equals(a.getStatus(), ProjectTaskStatusEnum.FINISHED.getStatus())).count());
            taskStatusStatsVO.setDone((int) projectTasks.stream().filter(a -> Objects.equals(a.getStatus(), ProjectTaskStatusEnum.FINISHED.getStatus())).count());
            taskStatusStatsVO.setDoneOverdue((int) projectTasks.stream().filter(a -> a.getCloseTime() != null && a.getCloseTime().getTime() < new Date().getTime() && Objects.equals(a.getStatus(), ProjectTaskStatusEnum.FINISHED.getStatus())).count());
            taskStatusStatsVO.setExpireToday((int) projectTasks.stream().filter(a -> a.getCloseTime() != null && DateUtils.dateTime(new Date()).compareTo(DateUtils.dateTime(a.getCloseTime())) == 0).count());
            taskStatusStatsVO.setTimeUndetermined((int) projectTasks.stream().filter(a -> a.getEndTime() == null).count());
            taskStatusStatsVO.setOverdue((int) projectTasks.stream().filter(a -> a.getCloseTime() != null && a.getCloseTime().getTime() < new Date().getTime()).count());

        } else {
            taskStatusStatsVO.setTotal(0);
            taskStatusStatsVO.setToBeAssign(0);
            taskStatusStatsVO.setUnDone(0);
            taskStatusStatsVO.setDone(0);
            taskStatusStatsVO.setDoneOverdue(0);
            taskStatusStatsVO.setExpireToday(0);
            taskStatusStatsVO.setTimeUndetermined(0);
            taskStatusStatsVO.setOverdue(0);
        }
        return taskStatusStatsVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void  deleteTask(TaskIdsVO taskIdsVO) {
        LambdaUpdateChainWrapper<ProjectTask> wrapper = lambdaUpdate().in(ProjectTask::getId, taskIdsVO.getTaskIdList());
        wrapper.set(ProjectTask::getDeleted, 1).set(ProjectTask::getDeletedTime, new Date());
        wrapper.update();
    }

    @Override
    public TaskResVO detail(TaskReqVO taskReqVO) {
        TaskResVO detail = projectTaskMapper.detail(taskReqVO.getTaskId());
        detail.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(detail.getStatus()));
        detail.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(detail.getExecuteStatus()));
        String createdBy = "";
        if (detail.getUserId() != null) {
            // 查询用户信息
            List<SysUser> sysUsers = getSysUserList(Collections.singletonList(detail.getUserId()));
            createdBy = sysUsers.get(0).getNickName();
            detail.setExecutor(createdBy);
        }
        detail.setCreatedBy(createdBy);
        detail.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(detail.getTaskPriority()));
        return detail;
    }

    private List<SysUser> getSysUserList(List<Long> userIds) {
        // 查询用户信息
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();
        return userVOList.stream()
                .map(userVO -> (SysUser) userVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectMemberResVO> queryExecutorList(TaskReqVO taskReqVO) {
        List<ProjectMemberResVO> list = projectMemberMapper.queryExecutorList(taskReqVO.getProjectId());
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(ProjectMemberResVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(projectMemberResVO -> {
            SysUserVO sysUserVO = userMap.get(projectMemberResVO.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                projectMemberResVO.setUserName(sysUserVO.getUserName());
                projectMemberResVO.setNickName(sysUserVO.getNickName());
                projectMemberResVO.setEmail(sysUserVO.getEmail());
                projectMemberResVO.setAvatar(sysUserVO.getAvatar());
            }
        });
        return list;
    }

    @Override
    public PageInfo<TaskResVO> list(TaskReqVO taskReqVO) {
        PageHelper.startPage(taskReqVO.getPageNum(), taskReqVO.getPageSize());
        List<TaskResVO> list = projectTaskMapper.list(taskReqVO, SecurityUtils.getUserId());
        if (CollectionUtils.isEmpty(list)) {
            return new PageInfo<>(list);
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(TaskResVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        if (StringUtils.isNotEmpty(taskReqVO.getExecutor())) {
            sysUserDTO.setNickName(taskReqVO.getExecutor());
        }
        if (StringUtils.isNotEmpty(taskReqVO.getCreatedBy())) {
            sysUserDTO.setNickName(taskReqVO.getCreatedBy());
        }
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(a -> {
            WorkFlowable workFlowable = new WorkFlowable();
            workFlowable.setTaskId(a.getTaskProcessId());
            workFlowable.setApproved(a.getApproved());
            workFlowable.setDeploymentId(a.getDeployId());
            workFlowable.setProcInsId(a.getProcInsId());
            workFlowable.setDefinitionId(a.getDefinitionId());
            a.setWorkFlowable(workFlowable);
            a.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(a.getTaskPriority()));
            a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus()));
            a.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getExecuteStatus()));
            if (a.getEndTime() != null && a.getBeginTime() != null) {
                a.setPeriod(DateUtils.differentDaysByMillisecond(a.getEndTime(), a.getBeginTime()));
            }
            // 设置用户信息
            SysUserVO sysUserVO = userMap.get(a.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                a.setExecutor(sysUserVO.getNickName());
                a.setCreatedBy(sysUserVO.getNickName());
            }
        });
        return new PageInfo<>(list);
    }

    @Override
    @GlobalTransactional(name = "pmhub-project-addTask",rollbackFor = Exception.class) //seata分布式事务，AT模式
    public String add(TaskReqVO taskReqVO) {
        // xid 全局事务id的检查（方便查看）
        String xid = RootContext.getXID();
        log.info("---------------开始新建任务: "+"\t"+"xid: "+xid);

        if (ProjectStatusEnum.PAUSE.getStatus().equals(projectTaskMapper.queryProjectStatus(taskReqVO.getProjectId()))) {
            throw new ServiceException("归属项目已暂停，无法新增任务");
        }

        // 1、添加任务
        ProjectTask projectTask = new ProjectTask();
        if (StringUtils.isNotBlank(taskReqVO.getTaskId())) {
            projectTask.setTaskPid(taskReqVO.getTaskId());
        }
        BeanUtils.copyProperties(taskReqVO, projectTask);
        projectTask.setCreatedBy(SecurityUtils.getUsername());
        projectTask.setCreatedTime(new Date());
        projectTask.setUpdatedBy(SecurityUtils.getUsername());
        projectTask.setUpdatedTime(new Date());
        projectTaskMapper.insert(projectTask);

        // 2、添加任务成员
        insertMember(projectTask.getId(), 1, SecurityUtils.getUserId());
        // 3、添加日志
        saveLog("addTask", projectTask.getId(), taskReqVO.getProjectId(), taskReqVO.getTaskName(), "参与了任务", null);
        // 将执行人加入
        if (taskReqVO.getUserId() != null && !Objects.equals(taskReqVO.getUserId(), SecurityUtils.getUserId())) {
            insertMember(projectTask.getId(), 0, taskReqVO.getUserId());
            // 添加日志
            saveLog("invitePartakeTask", projectTask.getId(), taskReqVO.getProjectId(), taskReqVO.getTaskName(), "邀请 " + getSysUserList(Collections.singletonList(taskReqVO.getUserId())).get(0).getNickName() + " 参与任务", taskReqVO.getUserId());
        }
        // 4、任务指派消息提醒
        extracted(taskReqVO.getTaskName(), taskReqVO.getUserId(), SecurityUtils.getUsername(), projectTask.getId());

        // 5、添加或更新审批设置（远程调用 pmhub-workflow 微服务）
        ApprovalSetDTO approvalSetDTO = new ApprovalSetDTO(projectTask.getId(), ProjectStatusEnum.TASK.getStatusName(),
                taskReqVO.getApproved(), taskReqVO.getDefinitionId(), taskReqVO.getDeploymentId());
        R<Boolean> result = wfDeployService.insertOrUpdateApprovalSet(approvalSetDTO, SecurityConstants.INNER);

        if (Objects.isNull(result) || Objects.isNull(result.getData())
                || R.fail().equals(result.getData())) {
            throw  new ServiceException("远程调用审批服务失败");
        }
        log.info("---------------结束新建任务: "+"\t"+"xid: "+xid);
        return projectTask.getId();
    }

    private void extracted(String taskName, Long userId, String username, String taskId) {
//        String name = projectTaskMapper.queryVxUserName(userId);
//        if (StringUtils.isNotBlank(name)) {
            // TODO: 2024.04.25 逾期任务提醒暂时关闭
//            TaskAssignRemindDTO taskAssignRemindDTO = new TaskAssignRemindDTO();
//            taskAssignRemindDTO.setTaskName(taskName);
//            taskAssignRemindDTO.setUserIds(Collections.singletonList(name));
//            taskAssignRemindDTO.setCreator(projectTaskMapper.queryNickName(username));
//            // 设置任务详情地址
//            String url = SsoUrlUtils.ssoCreate(appid, agentid, host + path + ssoPath + URLEncoder.encode(host + "/pmhub-project/my-task/info?taskId=" + taskId));
//            taskAssignRemindDTO.setDetailUrl(url);
//            taskAssignRemindDTO.setUserName(username);
//            taskAssignRemindDTO.setOaTitle("任务指派提醒");
//            taskAssignRemindDTO.setOaContext("【" + projectTaskMapper.queryNickName(username) + "】给您指派了任务【" + taskName + "】，请及时处理！");
//            taskAssignRemindDTO.setLinkUrl(OAUtils.ssoCreate(host + "/pmhub-project/my-task/info?taskId=" + taskId));
            // TODO: 2024.03.03 @canghe 推送消息暂时关闭
//            RocketMqUtils.push2Wx(taskAssignRemindDTO);
//        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(TaskReqVO taskReqVO) {
        ProjectTask oldObj = projectTaskMapper.selectById(taskReqVO.getTaskId());
        if (ProjectStatusEnum.PAUSE.getStatus().equals(projectTaskMapper.queryProjectStatus(oldObj.getProjectId()))) {
            throw new ServiceException("归属项目已暂停，无法操作任务");
        }
        if (ProjectStatusEnum.PAUSE.getStatus().equals(projectTaskMapper.queryProjectStatus(taskReqVO.getProjectId()))) {
            throw new ServiceException("该任务不能切换到已暂停的项目");
        }
         // TODO: 2024.06.24 暂时注释掉审批过滤，待远程调用
//        if (!Objects.equals(oldObj.getStatus(), taskReqVO.getStatus())) {
//            // 根据 taskId 去查询 是否需要审批
//            String queryApproved = projectTaskMapper.queryApproved(taskReqVO.getTaskId());
//            String approved = "0";
//            if (approved.equals(queryApproved)) {
//                throw new ServiceException("该任务需要审批，任务状态不允许手动修改");
//            } else {
//                if (!SecurityUtils.getUsername().equals(oldObj.getCreatedBy())) {
//                    throw new ServiceException("该任务不需要审批，只有创建人才能修改任务状态");
//                }
//            }
//        }
        ProjectTask projectTask = new ProjectTask();
        BeanUtils.copyProperties(taskReqVO, projectTask);
        projectTask.setId(taskReqVO.getTaskId());
        projectTask.setProjectId(taskReqVO.getProjectId());
        projectTask.setUpdatedTime(new Date());
        projectTaskMapper.updateById(projectTask);

        LambdaQueryWrapper<ProjectMember> qw = new LambdaQueryWrapper<>();
        qw.eq(ProjectMember::getPtId, taskReqVO.getTaskId()).eq(ProjectMember::getType, ProjectStatusEnum.TASK.getStatusName());
        List<ProjectMember> projectMembers = projectMemberMapper.selectList(qw);
        if (projectMembers.size() == 1) {
            if (!Objects.equals(taskReqVO.getUserId(), projectMembers.get(0).getUserId())) {
                ProjectMember projectMember = new ProjectMember();
                projectMember.setPtId(taskReqVO.getTaskId());
                projectMember.setType(ProjectStatusEnum.TASK.getStatusName());
                projectMember.setJoinedTime(new Date());
                projectMember.setUserId(taskReqVO.getUserId());
                projectMember.setCreatedBy(SecurityUtils.getUsername());
                projectMember.setCreatedTime(new Date());
                projectMember.setUpdatedBy(SecurityUtils.getUsername());
                projectMember.setUpdatedTime(new Date());
                projectMemberMapper.insert(projectMember);
            }
        } else if (projectMembers.size() == 2) {
            Map<Long, List<ProjectMember>> map = projectMembers.stream().collect(Collectors.groupingBy(ProjectMember::getUserId));
            List<ProjectMember> pms = map.get(taskReqVO.getUserId());
            if (CollectionUtils.isEmpty(pms)) {
                // 将creator为0的进行更新
                LambdaQueryWrapper<ProjectMember> lqw = new LambdaQueryWrapper<>();
                lqw.eq(ProjectMember::getPtId, taskReqVO.getTaskId()).eq(ProjectMember::getCreator, 0);
                ProjectMember projectMember = projectMemberMapper.selectOne(lqw);
                projectMember.setUserId(taskReqVO.getUserId());
                projectMember.setUpdatedBy(SecurityUtils.getUsername());
                projectMember.setUpdatedTime(new Date());
                projectMember.setJoinedTime(new Date());
                projectMemberMapper.updateById(projectMember);
            } else {
                if (pms.get(0).getCreator() == 1) {
                    // 删除creator为0的
                    LambdaQueryWrapper<ProjectMember> lqw = new LambdaQueryWrapper<>();
                    lqw.eq(ProjectMember::getPtId, taskReqVO.getTaskId()).eq(ProjectMember::getCreator, 0);
                    projectMemberMapper.delete(lqw);
                }
            }
        }
        if (!oldObj.getUserId().equals(taskReqVO.getUserId())) {
            // 任务指派消息提醒
            extracted(taskReqVO.getTaskName(), taskReqVO.getUserId(), SecurityUtils.getUsername(), taskReqVO.getTaskId());
        }
        ProjectTask newObj = projectTaskMapper.selectById(taskReqVO.getTaskId());
        List<LogDataVO> data = FieldUtils.getChangedFields(newObj, oldObj);
        data.forEach(a -> {
            // 添加日志
            LogVO lv = new LogVO();
            lv.setLogType(LogTypeEnum.TRENDS.getStatus());
            lv.setOperateType("editTask");
            lv.setType(ProjectStatusEnum.TASK.getStatusName());
            lv.setPtId(projectTask.getId());
            lv.setProjectId(projectTask.getProjectId());
            lv.setUserId(SecurityUtils.getUserId());

            lv.setRemark(a.getRemark());
            List<LogContentVO> logContentVOList = a.getLogContentVOList();
            logContentVOList.forEach(logContentVO -> {
                switch (logContentVO.getField()) {
                    case "userId":
                        logContentVO.setOldValue(getSysUserList(Collections.singletonList(Long.valueOf(logContentVO.getOldValue()))).get(0).getNickName());
                        logContentVO.setNewValue(getSysUserList(Collections.singletonList(Long.valueOf(logContentVO.getNewValue()))).get(0).getNickName());
                        break;
                    case "status":
                    case "executeStatus":
                        logContentVO.setOldValue(ProjectTaskStatusEnum.getStatusNameByStatus(Integer.parseInt(logContentVO.getOldValue())));
                        logContentVO.setNewValue(ProjectTaskStatusEnum.getStatusNameByStatus(Integer.parseInt(logContentVO.getNewValue())));
                        break;
                    case "taskPriority":
                        logContentVO.setOldValue(ProjectTaskPriorityEnum.getStatusNameByStatus(Integer.parseInt(logContentVO.getOldValue())));
                        logContentVO.setNewValue(ProjectTaskPriorityEnum.getStatusNameByStatus(Integer.parseInt(logContentVO.getNewValue())));
                        break;
                }
            });
            lv.setContent(JSON.toJSONString(logContentVOList));
            lv.setCreatedBy(SecurityUtils.getUsername());
            lv.setCreatedTime(new Date());
            lv.setUpdatedBy(SecurityUtils.getUsername());
            lv.setUpdatedTime(new Date());
            projectLogService.run(lv);
            if (ProjectTaskStatusEnum.FINISHED.getStatus().equals(taskReqVO.getStatus())) {
                projectTask.setTaskProcess(new BigDecimal("100"));
            }
            projectTaskMapper.updateById(projectTask);
        });
    }

    @Override
    public List<TaskResVO> queryChildTask(TaskReqVO taskReqVO) {
        List<TaskResVO> taskResVOList = projectTaskMapper.queryChildTask(taskReqVO.getTaskId());
        taskResVOList.forEach(detail -> {
            detail.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(detail.getStatus()));
            detail.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(detail.getExecuteStatus()));
            String createdBy = "";
            if (detail.getUserId() != null) {
                createdBy = getSysUserList(Collections.singletonList(detail.getUserId())).get(0).getNickName();
                detail.setExecutor(createdBy);
            }
            detail.setCreatedBy(createdBy);
            detail.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(detail.getTaskPriority()));
        });
        return taskResVOList;
    }

    @Override
    public List<BurnDownChartVO> burnDownChart(ProjectVO projectVO) {
        List<BurnDownChartVO> list = new ArrayList<>(10);
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectTask::getProjectId, projectVO.getProjectId()).orderByAsc(ProjectTask::getCreatedTime);
        List<ProjectTask> projectTasks = projectTaskMapper.selectList(queryWrapper);

        if (CollectionUtils.isNotEmpty(projectTasks)) {
            Date createdTime = projectTasks.get(0).getCreatedTime();
            String beginDate = DateUtils.dateTime(createdTime);
            String endDate = DateUtils.dateTime(new Date());
            List<String> betweenDate = DateUtils.getBetweenDate(beginDate, endDate);
            betweenDate.forEach(date -> {
                LocalDate now = LocalDate.parse(date, DateTimeFormatter.ofPattern(DateUtils.YYYY_MM_DD)).plusDays(1);
                BurnDownChartVO burnDownChartVO = new BurnDownChartVO();
                burnDownChartVO.setDate(date);
                LambdaQueryWrapper<ProjectTask> qw = new LambdaQueryWrapper<>();
                qw.eq(ProjectTask::getProjectId, projectVO.getProjectId()).lt(ProjectTask::getCreatedTime, now);
                List<ProjectTask> projectTasks2 = projectTaskMapper.selectList(qw);
                burnDownChartVO.setTaskNum(projectTasks2.size());
                burnDownChartVO.setUnDoneTaskNum((int) projectTasks2.stream().filter(a -> !Objects.equals(a.getStatus(), ProjectTaskStatusEnum.FINISHED.getStatus())).count());
                burnDownChartVO.setBaseLineNum((int) projectTasks2.stream().filter(a -> !Objects.equals(a.getStatus(), ProjectTaskStatusEnum.FINISHED.getStatus())).filter(o -> {
                    if (o.getEndTime() == null) {
                        if (o.getCreatedTime() != null) {
                            Instant instant = o.getCreatedTime().toInstant();
                            ZoneId zoneId = ZoneId.systemDefault();
                            LocalDate create = instant.atZone(zoneId).toLocalDate();
                            return create.plusDays(5).isAfter(now);
                        }
                        return true;
                    } else {
                        Instant instant = o.getEndTime().toInstant();
                        ZoneId zoneId = ZoneId.systemDefault();
                        LocalDate end = instant.atZone(zoneId).toLocalDate();
                        return end.plusDays(-1).isBefore(now);
                    }
                }).count());
                list.add(burnDownChartVO);
            });
        }
        return list;
    }

    @Override
    public List<ProjectMemberResVO> queryUserList(ProjectTaskReqVO projectTaskReqVO) {
        List<ProjectMemberResVO> list = projectMemberMapper.queryTaskUserList(projectTaskReqVO.getTaskId());
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(ProjectMemberResVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(projectMemberResVO -> {
            SysUserVO sysUserVO = userMap.get(projectMemberResVO.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                projectMemberResVO.setUserName(sysUserVO.getUserName());
                projectMemberResVO.setNickName(sysUserVO.getNickName());
            }
        });
        return list;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(TaskCommentVO taskCommentVO) {
        ProjectLog projectLog = new ProjectLog();
        projectLog.setProjectId(taskCommentVO.getProjectId());
        projectLog.setOperateType("comment");
        projectLog.setUserId(SecurityUtils.getUserId());
        projectLog.setRemark("添加了评论");
        projectLog.setContent(taskCommentVO.getComment());
        projectLog.setLogType(LogTypeEnum.COMMENT.getStatus());
        projectLog.setPtId(taskCommentVO.getTaskId());
        projectLog.setType(ProjectStatusEnum.TASK.getStatusName());
        projectLog.setCreatedBy(SecurityUtils.getUsername());
        projectLog.setCreatedTime(new Date());
        projectLog.setUpdatedBy(SecurityUtils.getUsername());
        projectLog.setUpdatedTime(new Date());
        projectLogService.save(projectLog);
    }

    /**
     *
     * @param logReqVO
     * @return
     */
    @Override
    public List<ProjectLogVO> queryTaskLogList(LogReqVO logReqVO) {
        PageHelper.startPage(logReqVO.getPageNum(), logReqVO.getPageSize());
        return queryTaskLogFactory.execute(logReqVO.getLogType(), logReqVO.getTaskId());
    }

    @Override
    public void downloadTemplate(String taskId, HttpServletResponse response) throws IOException {

        // 根据 taskId 查询最新的模板
        LambdaQueryWrapper<ProjectFile> lw = new LambdaQueryWrapper<>();
        lw.eq(ProjectFile::getPtId, taskId).eq(ProjectFile::getType, ProjectStatusEnum.TEMPLATE.getStatusName()).orderByDesc(ProjectFile::getCreatedTime);
        List<ProjectFile> projectFiles = projectFileMapper.selectList(lw);
        if (CollectionUtils.isEmpty(projectFiles)) {
            throw new ServerException("不存在模板文件，请上传之后再下载");
        } else {
            String filePath = projectFiles.get(0).getPathName();
            String fileUrl = projectFiles.get(0).getFileUrl();
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, fileUrl.substring(fileUrl.lastIndexOf("/") + 1));
            FileUtils.writeBytes(filePath, response);
        }

    }

    @Override
    public List<TaskExportVO> exportAll() {
        List<TaskExportVO> list = projectTaskMapper.exportAll(SecurityUtils.getUserId());
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(TaskExportVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(a -> {
            a.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getExecuteStatus()));
            a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus()));
            a.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(a.getTaskPriority()));

            // 设置用户信息
            SysUserVO sysUserVO = userMap.get(a.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                a.setExecutor(sysUserVO.getNickName());
                a.setCreatedBy(sysUserVO.getNickName());
            }
        });
        return list;
    }

    @Override
    public List<TaskExportVO> export(String taskIds) {
        List<String> taskIdList = Arrays.asList(taskIds.split(","));
        List<TaskExportVO> list = projectTaskMapper.export(taskIdList);

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(TaskExportVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(a -> {
            a.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getExecuteStatus()));
            a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus()));
            a.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(a.getTaskPriority()));

            // 设置用户信息
            SysUserVO sysUserVO = userMap.get(a.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                a.setExecutor(sysUserVO.getNickName());
                a.setCreatedBy(sysUserVO.getNickName());
            }
        });

        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importTask(List<TaskExcelVO> taskList) {
        if (CollectionUtils.isEmpty(taskList)) {
            throw new ServiceException("导入任务数据不能为空");
        }
        // todo 后期优化成批量查询，性能优化
        taskList.forEach(task -> {
            // 查询用户信息
            R<LoginUser> userResult = userFeignService.info(task.getUsername(), SecurityConstants.INNER);

            if (Objects.isNull(userResult) || Objects.isNull(userResult.getData())) {
                throw new ServiceException("登录用户：" + task.getUsername() + " 不存在");
            }

            LoginUser loginUser = userResult.getData();
            if (Objects.isNull(loginUser)) {
                return;
            }
            SysUser sysUser =  loginUser.getUser();

            ProjectTask projectTask = new ProjectTask();
            projectTask.setTaskName(task.getTaskName());
            projectTask.setBeginTime(DateUtils.parseDate(task.getBeginTime()));
            projectTask.setEndTime(DateUtils.parseDate(task.getEndTime()));
            projectTask.setCloseTime(DateUtils.parseDate(task.getCloseTime()));
            projectTask.setTaskPriority(Integer.valueOf(task.getTaskPriority()));
            LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<>();
            qw.eq(Project::getProjectCode, task.getProjectCode());
            String projectId = projectMapper.selectOne(qw).getId();
            if (StringUtils.isBlank(projectId)) {
                return;
            }
            // 根据项目id查询成员
            LambdaQueryWrapper<ProjectMember> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ProjectMember::getPtId, projectId).eq(ProjectMember::getType, ProjectStatusEnum.PROJECT.getStatusName());
            List<ProjectMember> projectMembers = projectMemberMapper.selectList(queryWrapper);
            List<Long> userIds = projectMembers.stream().map(ProjectMember::getUserId).collect(Collectors.toList());
            if (!userIds.contains(sysUser.getUserId())) {
                return;
            }
            projectTask.setProjectId(projectId);
            LambdaQueryWrapper<ProjectStage> qw2 = new LambdaQueryWrapper<>();
            qw2.eq(ProjectStage::getProjectId, projectId).orderByAsc(ProjectStage::getStageCode);
            projectTask.setProjectStageId(projectStageMapper.selectList(qw2).get(0).getId());
            projectTask.setUserId(sysUser.getUserId());
            projectTask.setCreatedBy(SecurityUtils.getUsername());
            projectTask.setCreatedTime(new Date());
            projectTask.setUpdatedBy(SecurityUtils.getUsername());
            projectTask.setUpdatedTime(new Date());
            projectTaskMapper.insert(projectTask);
            insertMember(projectTask.getId(), 1, SecurityUtils.getUserId());
            // 添加日志
            saveLog("importTask", projectTask.getId(), projectTask.getProjectId(), projectTask.getTaskName()
                    , "导入了任务", null);
            // 将执行人加入
            if (projectTask.getUserId() != null && !Objects.equals(projectTask.getUserId(), SecurityUtils.getUserId())) {
                insertMember(projectTask.getId(), 0, projectTask.getUserId());
                // 添加日志
                saveLog("invitePartakeTask", projectTask.getId(), projectTask.getProjectId(), projectTask.getTaskName()
                        ,"邀请 " + getSysUserList(Collections.singletonList(projectTask.getUserId())).get(0).getNickName() + " 参与任务"
                        , projectTask.getUserId());
            }
        });
    }

    void insertMember(String taskId, Integer creator, Long userId) {
        ProjectMember projectMember = new ProjectMember();
        projectMember.setPtId(taskId);
        projectMember.setType(ProjectStatusEnum.TASK.getStatusName());
        projectMember.setJoinedTime(new Date());
        projectMember.setUserId(userId);
        projectMember.setCreatedBy(SecurityUtils.getUsername());
        projectMember.setCreatedTime(new Date());
        projectMember.setUpdatedBy(SecurityUtils.getUsername());
        projectMember.setUpdatedTime(new Date());
        // 是创建者
        projectMember.setCreator(creator);
        projectMemberMapper.insert(projectMember);
    }

    void saveLog(String operateType, String taskId, String projectId, String taskName, String remark, Long userId) {
        LogVO logVO = new LogVO();
        logVO.setLogType(LogTypeEnum.TRENDS.getStatus());
        logVO.setOperateType(operateType);
        logVO.setType(ProjectStatusEnum.TASK.getStatusName());
        logVO.setPtId(taskId);
        logVO.setProjectId(projectId);
        logVO.setUserId(SecurityUtils.getUserId());
        if (userId != null) {
            logVO.setToUserId(userId);
        }
        logVO.setRemark(remark);
        logVO.setContent(taskName);
        logVO.setCreatedBy(SecurityUtils.getUsername());
        logVO.setCreatedTime(new Date());
        logVO.setUpdatedBy(SecurityUtils.getUsername());
        logVO.setUpdatedTime(new Date());
        projectLogService.run(logVO);
    }

    @Override
    public void downloadTaskTemplate(HttpServletResponse response) throws IOException {
        String filePath = PmhubConfig.getProfile() + "/template/taskTemplate.xlsx";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, "任务模板.xlsx");
        FileUtils.writeBytes(filePath, response);
    }

    @Override
    public PageInfo<TaskResVO> taskList(TaskReqVO taskReqVO) {
        PageHelper.startPage(taskReqVO.getPageNum(), taskReqVO.getPageSize());
        List<TaskResVO> list = projectTaskMapper.taskList(taskReqVO);
        if (CollectionUtils.isEmpty(list)) {
            return new PageInfo<>(list);
        }
        // 拿到userids
        List<Long> userIds = list.stream().map(TaskResVO::getUserId)
                .distinct()
                .collect(Collectors.toList());
        SysUserDTO sysUserDTO = new SysUserDTO();
        sysUserDTO.setUserIds(userIds);
        if (StringUtils.isNotEmpty(taskReqVO.getExecutor())) {
            sysUserDTO.setNickName(taskReqVO.getExecutor());
        }
        if (StringUtils.isNotEmpty(taskReqVO.getCreatedBy())) {
            sysUserDTO.setNickName(taskReqVO.getCreatedBy());
        }
        R<List<SysUserVO>> userResult = userFeignService.listOfInner(sysUserDTO, SecurityConstants.INNER);

        if (Objects.isNull(userResult) || CollectionUtils.isEmpty(userResult.getData())) {
            throw new ServiceException("远程调用查询用户列表：" + userIds + " 失败");
        }
        List<SysUserVO> userVOList = userResult.getData();

        // 匹配设置值
        Map<Long, SysUserVO> userMap = userVOList.stream().collect(Collectors.toMap(SysUserVO::getUserId, a -> a));
        list.forEach(a -> {
            a.setTaskPriorityName(ProjectTaskPriorityEnum.getStatusNameByStatus(a.getTaskPriority()));
            a.setStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getStatus()));
            a.setExecuteStatusName(ProjectTaskStatusEnum.getStatusNameByStatus(a.getExecuteStatus()));
            if (a.getEndTime() != null && a.getBeginTime() != null) {
                a.setPeriod(DateUtils.differentDaysByMillisecond(a.getEndTime(), a.getBeginTime()));
            }
            WorkFlowable workFlowable = new WorkFlowable();
            workFlowable.setTaskId(a.getTaskProcessId());
            workFlowable.setApproved(a.getApproved());
            workFlowable.setDeploymentId(a.getDeployId());
            workFlowable.setProcInsId(a.getProcInsId());
            workFlowable.setDefinitionId(a.getDefinitionId());
            a.setWorkFlowable(workFlowable);
            // 设置用户信息
            SysUserVO sysUserVO = userMap.get(a.getUserId());
            if (Objects.nonNull(sysUserVO)) {
                a.setExecutor(sysUserVO.getNickName());
                a.setCreatedBy(sysUserVO.getNickName());
            }
        });
        return new PageInfo<>(list);
    }

    @Override
    public Long countTaskNum() {
        LambdaQueryWrapper<ProjectTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectTask::getDeleted, 0);
        if (projectTaskMapper.selectCount(queryWrapper) == null) {
            return 0L;
        }
        return projectTaskMapper.selectCount(queryWrapper);
    }

    @Override
    public List<Project> queryProjectsStatus(List<String> projectIds) {
        return projectTaskMapper.queryProjectsStatus(projectIds);
    }

    @Override
    public List<ProjectTaskProcess> taskProcessList(List<String> taskIds) {
        LambdaQueryWrapper<ProjectTaskProcess> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.in(ProjectTaskProcess::getExtraId, taskIds);
        return projectTaskProcessMapper.selectList(queryWrapper);
    }

}
