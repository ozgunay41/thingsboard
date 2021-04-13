/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.UUID;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class AlarmController extends BaseController {
    public static final String TENANT_ID = "tenantId";
    public static final String ALARM_ID = "alarmId";

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/{alarmId}", method = RequestMethod.GET)
    @ResponseBody
    public Alarm getAlarmById(
        @PathVariable(ALARM_ID) String strAlarmId,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter(ALARM_ID, strAlarmId);
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            AlarmId alarmId = new AlarmId(toUUID(strAlarmId));
            return checkAlarmId(alarmId, Operation.READ, tenantId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/info/{alarmId}", method = RequestMethod.GET)
    @ResponseBody
    public AlarmInfo getAlarmInfoById(
        @PathVariable(ALARM_ID) String strAlarmId,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter(ALARM_ID, strAlarmId);
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            AlarmId alarmId = new AlarmId(toUUID(strAlarmId));
            return checkAlarmInfoId(alarmId, Operation.READ, tenantId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm", method = RequestMethod.POST)
    @ResponseBody
    public Alarm saveAlarm(
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId,
        @RequestBody Alarm alarm)
        throws ThingsboardException {
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            alarm.setTenantId(tenantId);
            checkEntity(alarm.getId(), alarm, Resource.ALARM, tenantId);
            Alarm savedAlarm = checkNotNull(alarmService.createOrUpdateAlarm(alarm));
            logEntityAction(
                savedAlarm.getOriginator(),
                savedAlarm,
                getCurrentUser().getCustomerId(),
                alarm.getId() == null ? ActionType.ADDED : ActionType.UPDATED,
                null);
            return savedAlarm;
        } catch (Exception e) {
            logEntityAction(
                emptyId(EntityType.ALARM),
                alarm,
                    null, alarm.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/{alarmId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Boolean deleteAlarm(
        @PathVariable(ALARM_ID) String strAlarmId,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter(ALARM_ID, strAlarmId);
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            AlarmId alarmId = new AlarmId(toUUID(strAlarmId));
            checkAlarmId(alarmId, Operation.WRITE, tenantId);
            return alarmService.deleteAlarm(tenantId, alarmId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/{alarmId}/ack", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void ackAlarm(
        @PathVariable(ALARM_ID) String strAlarmId,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId) 
        throws ThingsboardException {
        checkParameter(ALARM_ID, strAlarmId);
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            AlarmId alarmId = new AlarmId(toUUID(strAlarmId));
            Alarm alarm = checkAlarmId(alarmId, Operation.WRITE, tenantId);
            long ackTs = System.currentTimeMillis();
            alarmService.ackAlarm(tenantId, alarmId, ackTs).get();
            alarm.setAckTs(ackTs);
            alarm.setStatus(alarm.getStatus().isCleared() ? AlarmStatus.CLEARED_ACK : AlarmStatus.ACTIVE_ACK);
            logEntityAction(alarm.getOriginator(), alarm, getCurrentUser().getCustomerId(), ActionType.ALARM_ACK, null);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/{alarmId}/clear", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void clearAlarm(
        @PathVariable(ALARM_ID) String strAlarmId,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter(ALARM_ID, strAlarmId);
        try {
            TenantId tenantId = getTenantId(requestTenantId);
            AlarmId alarmId = new AlarmId(toUUID(strAlarmId));
            Alarm alarm = checkAlarmId(alarmId, Operation.WRITE, tenantId);
            long clearTs = System.currentTimeMillis();
            alarmService.clearAlarm(tenantId, alarmId, null, clearTs).get();
            alarm.setClearTs(clearTs);
            alarm.setStatus(alarm.getStatus().isAck() ? AlarmStatus.CLEARED_ACK : AlarmStatus.CLEARED_UNACK);
            logEntityAction(alarm.getOriginator(), alarm, getCurrentUser().getCustomerId(), ActionType.ALARM_CLEAR, null);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public PageData<AlarmInfo> getAlarms(
        @PathVariable("entityType") String strEntityType,
        @PathVariable("entityId") String strEntityId,
        @RequestParam(required = false) String searchStatus,
        @RequestParam(required = false) String status,
        @RequestParam int pageSize,
        @RequestParam int page,
        @RequestParam(required = false) String textSearch,
        @RequestParam(required = false) String sortProperty,
        @RequestParam(required = false) String sortOrder,
        @RequestParam(required = false) Long startTime,
        @RequestParam(required = false) Long endTime,
        @RequestParam(required = false) String offset,
        @RequestParam(required = false) Boolean fetchOriginator,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        checkParameter("EntityType", strEntityType);
        TenantId tenantId = getTenantId(requestTenantId);
        EntityId entityId = EntityIdFactory.getByTypeAndId(strEntityType, strEntityId);
        AlarmSearchStatus alarmSearchStatus = StringUtils.isEmpty(searchStatus) ? null : AlarmSearchStatus.valueOf(searchStatus);
        AlarmStatus alarmStatus = StringUtils.isEmpty(status) ? null : AlarmStatus.valueOf(status);
        if (alarmSearchStatus != null && alarmStatus != null) {
            throw new ThingsboardException(
                "Invalid alarms search query: Both parameters 'searchStatus' " +
                "and 'status' can't be specified at the same time!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        checkEntityId(entityId, Operation.READ, tenantId);
        TimePageLink pageLink = createTimePageLink(pageSize, page, textSearch, sortProperty, sortOrder, startTime, endTime);
        UUID idOffsetUuid = null;
        if (StringUtils.isNotEmpty(offset)) {
            idOffsetUuid = toUUID(offset);
        }
        try {
            return checkNotNull(alarmService.findAlarms(tenantId, new AlarmQuery(entityId, pageLink, alarmSearchStatus, alarmStatus, fetchOriginator, idOffsetUuid)).get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/highestSeverity/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public AlarmSeverity getHighestAlarmSeverity(
        @PathVariable("entityType") String strEntityType,
        @PathVariable("entityId") String strEntityId,
        @RequestParam(required = false) String searchStatus,
        @RequestParam(required = false) String status,
        @RequestParam(name = TENANT_ID, required = false) String requestTenantId)
        throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        checkParameter("EntityType", strEntityType);
        TenantId tenantId = getTenantId(requestTenantId);
        EntityId entityId = EntityIdFactory.getByTypeAndId(strEntityType, strEntityId);
        AlarmSearchStatus alarmSearchStatus = StringUtils.isEmpty(searchStatus) ? null : AlarmSearchStatus.valueOf(searchStatus);
        AlarmStatus alarmStatus = StringUtils.isEmpty(status) ? null : AlarmStatus.valueOf(status);
        if (alarmSearchStatus != null && alarmStatus != null) {
            throw new ThingsboardException(
                "Invalid alarms search query: Both parameters 'searchStatus' " +
                "and 'status' can't be specified at the same time!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        checkEntityId(entityId, Operation.READ, tenantId);
        try {
            return alarmService.findHighestAlarmSeverity(tenantId, entityId, alarmSearchStatus, alarmStatus);
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
