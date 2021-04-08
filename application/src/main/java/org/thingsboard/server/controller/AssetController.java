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

import com.google.common.util.concurrent.ListenableFuture;
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
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetInfo;
import org.thingsboard.server.common.data.asset.AssetSearchQuery;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.asset.BaseAssetService.TB_SERVICE_QUEUE;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class AssetController extends BaseController {

    public static final String ASSET_ID = "assetId";

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/{assetId}", method = RequestMethod.GET)
    @ResponseBody
    public Asset getAssetById(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            return checkAssetId(assetId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/info/{assetId}", method = RequestMethod.GET)
    @ResponseBody
    public AssetInfo getAssetInfoById(@PathVariable(ASSET_ID) String strAssetId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            AssetId assetId = new AssetId(toUUID(strAssetId));
            return checkAssetInfoId(assetId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset", method = RequestMethod.POST)
    @ResponseBody
    public Asset saveAsset(@RequestBody Asset asset, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();

            if (TB_SERVICE_QUEUE.equals(asset.getType())) {
                throw new ThingsboardException("Unable to save asset with type " + TB_SERVICE_QUEUE, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }

            asset.setTenantId(currentTenantId);

           checkEntity(asset.getId(), asset, Resource.ASSET);

            Asset savedAsset = checkNotNull(assetService.saveAsset(asset));

            logEntityAction(savedAsset.getId(), savedAsset,
                    savedAsset.getCustomerId(),
                    asset.getId() == null ? ActionType.ADDED : ActionType.UPDATED, null);

            return savedAsset;
        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ASSET), asset,
                    null, asset.getId() == null ? ActionType.ADDED : ActionType.UPDATED, e);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/asset/{assetId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteAsset(@PathVariable(ASSET_ID) String strAssetId, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();

            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.DELETE);
            assetService.deleteAsset(currentTenantId, assetId);

            logEntityAction(assetId, asset,
                    asset.getCustomerId(),
                    ActionType.DELETED, null, strAssetId);

        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.ASSET),
                    null,
                    null,
                    ActionType.DELETED, e, strAssetId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/customer/{customerId}/asset/{assetId}", method = RequestMethod.POST)
    @ResponseBody
    public Asset assignAssetToCustomer(@PathVariable("customerId") String strCustomerId,
                                       @PathVariable(ASSET_ID) String strAssetId,
                                       @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        checkParameter(ASSET_ID, strAssetId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            Customer customer = checkCustomerId(customerId, Operation.READ);

            AssetId assetId = new AssetId(toUUID(strAssetId));
            checkAssetId(assetId, Operation.ASSIGN_TO_CUSTOMER);

            Asset savedAsset = checkNotNull(assetService.assignAssetToCustomer(currentTenantId, assetId, customerId));

            logEntityAction(assetId, savedAsset,
                    savedAsset.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strAssetId, strCustomerId, customer.getName());

            return savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strAssetId, strCustomerId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/customer/asset/{assetId}", method = RequestMethod.DELETE)
    @ResponseBody
    public Asset unassignAssetFromCustomer(@PathVariable(ASSET_ID) String strAssetId, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.UNASSIGN_FROM_CUSTOMER);
            if (asset.getCustomerId() == null || asset.getCustomerId().getId().equals(ModelConstants.NULL_UUID)) {
                throw new IncorrectParameterException("Asset isn't assigned to any customer!");
            }

            Customer customer = checkCustomerId(asset.getCustomerId(), Operation.READ);

            Asset savedAsset = checkNotNull(assetService.unassignAssetFromCustomer(currentTenantId, assetId));

            logEntityAction(assetId, asset,
                    asset.getCustomerId(),
                    ActionType.UNASSIGNED_FROM_CUSTOMER, null, strAssetId, customer.getId().toString(), customer.getName());

            return savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.UNASSIGNED_FROM_CUSTOMER, e, strAssetId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/customer/public/asset/{assetId}", method = RequestMethod.POST)
    @ResponseBody
    public Asset assignAssetToPublicCustomer(@PathVariable(ASSET_ID) String strAssetId, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter(ASSET_ID, strAssetId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            AssetId assetId = new AssetId(toUUID(strAssetId));
            Asset asset = checkAssetId(assetId, Operation.ASSIGN_TO_CUSTOMER);
            Customer publicCustomer = customerService.findOrCreatePublicCustomer(asset.getTenantId());
            Asset savedAsset = checkNotNull(assetService.assignAssetToCustomer(currentTenantId, assetId, publicCustomer.getId()));

            logEntityAction(assetId, savedAsset,
                    savedAsset.getCustomerId(),
                    ActionType.ASSIGNED_TO_CUSTOMER, null, strAssetId, publicCustomer.getId().toString(), publicCustomer.getName());

            return savedAsset;
        } catch (Exception e) {

            logEntityAction(emptyId(EntityType.ASSET), null,
                    null,
                    ActionType.ASSIGNED_TO_CUSTOMER, e, strAssetId);

            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/assets", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Asset> getTenantAssets(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length()>0) {
                return checkNotNull(assetService.findAssetsByTenantIdAndType(currentTenantId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetsByTenantId(currentTenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/assetInfos", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<AssetInfo> getTenantAssetInfos(
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length() > 0) {
                return checkNotNull(assetService.findAssetInfosByTenantIdAndType(currentTenantId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetInfosByTenantId(currentTenantId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/assets", params = {"assetName"}, method = RequestMethod.GET)
    @ResponseBody
    public Asset getTenantAsset(
            @RequestParam String assetName, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            return checkNotNull(assetService.findAssetByTenantIdAndName(currentTenantId, assetName));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/assets", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<Asset> getCustomerAssets(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length()>0) {
                return checkNotNull(assetService.findAssetsByTenantIdAndCustomerIdAndType(currentTenantId, customerId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetsByTenantIdAndCustomerId(currentTenantId, customerId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/customer/{customerId}/assetInfos", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<AssetInfo> getCustomerAssetInfos(
            @PathVariable("customerId") String strCustomerId,
            @RequestParam int pageSize,
            @RequestParam int page,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String sortProperty,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkParameter("customerId", strCustomerId);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            CustomerId customerId = new CustomerId(toUUID(strCustomerId));
            checkCustomerId(customerId, Operation.READ);
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            if (type != null && type.trim().length() > 0) {
                return checkNotNull(assetService.findAssetInfosByTenantIdAndCustomerIdAndType(currentTenantId, customerId, type, pageLink));
            } else {
                return checkNotNull(assetService.findAssetInfosByTenantIdAndCustomerId(currentTenantId, customerId, pageLink));
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/assets", params = {"assetIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<Asset> getAssetsByIds(
            @RequestParam("assetIds") String[] strAssetIds, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkArrayParameter("assetIds", strAssetIds);
        try {
            SecurityUser user = getCurrentUser();
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            CustomerId customerId = user.getCustomerId();
            List<AssetId> assetIds = new ArrayList<>();
            for (String strAssetId : strAssetIds) {
                assetIds.add(new AssetId(toUUID(strAssetId)));
            }
            ListenableFuture<List<Asset>> assets;
            if (customerId == null || customerId.isNullUid()) {
                assets = assetService.findAssetsByTenantIdAndIdsAsync(currentTenantId, assetIds);
            } else {
                assets = assetService.findAssetsByTenantIdCustomerIdAndIdsAsync(currentTenantId, customerId, assetIds);
            }
            return checkNotNull(assets.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/assets", method = RequestMethod.POST)
    @ResponseBody
    public List<Asset> findByQuery(@RequestBody AssetSearchQuery query, @RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        checkNotNull(query);
        checkNotNull(query.getParameters());
        checkNotNull(query.getAssetTypes());
        checkEntityId(query.getParameters().getEntityId(), Operation.READ);
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            List<Asset> assets = checkNotNull(assetService.findAssetsByQuery(currentTenantId, query).get());
            assets = assets.stream().filter(asset -> {
                try {
                    accessControlService.checkPermission(getCurrentUser(), Resource.ASSET, Operation.READ, asset.getId(), asset);
                    return true;
                } catch (ThingsboardException e) {
                    return false;
                }
            }).collect(Collectors.toList());
            return assets;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('ROOT', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/asset/types", method = RequestMethod.GET)
    @ResponseBody
    public List<EntitySubtype> getAssetTypes(@RequestParam(name = "tenantId", required = false) TenantId tenantId) throws ThingsboardException {
        try {
            TenantId currentTenantId =
            getAuthority() == Authority.ROOT && tenantId != null
                ? tenantId
                : getTenantId();
            ListenableFuture<List<EntitySubtype>> assetTypes = assetService.findAssetTypesByTenantId(currentTenantId);
            return checkNotNull(assetTypes.get());
        } catch (Exception e) {
            throw handleException(e);
        }
    }
}
