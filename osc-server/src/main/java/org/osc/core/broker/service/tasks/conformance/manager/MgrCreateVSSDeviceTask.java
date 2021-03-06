/*******************************************************************************
 * Copyright (c) Intel Corporation
 * Copyright (c) 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.osc.core.broker.service.tasks.conformance.manager;

import java.util.Set;

import javax.persistence.EntityManager;

import org.osc.core.broker.job.lock.LockObjectReference;
import org.osc.core.broker.model.entities.appliance.DistributedApplianceInstance;
import org.osc.core.broker.model.entities.appliance.VirtualSystem;
import org.osc.core.broker.model.plugin.ApiFactoryService;
import org.osc.core.broker.service.persistence.OSCEntityManager;
import org.osc.core.broker.service.tasks.TransactionalTask;
import org.slf4j.LoggerFactory;
import org.osc.sdk.manager.api.ManagerDeviceApi;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;

/**
 * Creates the VSS device and updates the VS entity.
 */
@Component(service = MgrCreateVSSDeviceTask.class)
public class MgrCreateVSSDeviceTask extends TransactionalTask {
    private static final Logger log = LoggerFactory.getLogger(MgrCreateVSSDeviceTask.class);

    private VirtualSystem vs;

    @Reference
    private ApiFactoryService apiFactoryService;

    public MgrCreateVSSDeviceTask create(VirtualSystem vs) {
        MgrCreateVSSDeviceTask task = new MgrCreateVSSDeviceTask();
        task.apiFactoryService = this.apiFactoryService;
        task.vs = vs;
        task.name = task.getName();
        task.dbConnectionManager = this.dbConnectionManager;
        task.txBroadcastUtil = this.txBroadcastUtil;

        return task;
    }

    @Override
    public void executeTransaction(EntityManager em) throws Exception {

        this.vs = em.find(VirtualSystem.class, this.vs.getId());

        ManagerDeviceApi mgrApi = this.apiFactoryService.createManagerDeviceApi(this.vs);
        String deviceId = null;

        try {

            deviceId = mgrApi.createVSSDevice();
            this.vs.setMgrId(deviceId);
            OSCEntityManager.update(em, this.vs, this.txBroadcastUtil);
            log.info("New VSS device (" + deviceId + ") successfully created.");

        } catch (Exception e) {

            log.info("Failed to create device in Manager.");
            deviceId = mgrApi.findDeviceByName(this.vs.getName());
            if (deviceId != null) {
                this.vs.setMgrId(deviceId);
                OSCEntityManager.update(em, this.vs, this.txBroadcastUtil);
            } else {
                throw e;
            }
        } finally {
            mgrApi.close();
        }

        //Update the latest id for DAI's under the new VSS
        Set<DistributedApplianceInstance> daiList = this.vs.getDistributedApplianceInstances();
        daiList.forEach(dai -> {
            dai.setMgrDeviceId(null);
            OSCEntityManager.update(em, dai, this.txBroadcastUtil);
        });
    }

    @Override
    public String getName() {
        return "Create Manager VSS Device for '" + this.vs.getVirtualizationConnector().getName() + "'";
    }

    @Override
    public Set<LockObjectReference> getObjects() {
        return LockObjectReference.getObjectReferences(this.vs);
    }

}
