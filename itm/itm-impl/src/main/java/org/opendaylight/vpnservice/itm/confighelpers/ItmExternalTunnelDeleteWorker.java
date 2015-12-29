/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class ItmExternalTunnelDeleteWorker {
    private static final Logger logger = LoggerFactory.getLogger(ItmExternalTunnelDeleteWorker.class ) ;

    public static List<ListenableFuture<Void>> deleteTunnels(DataBroker dataBroker, List<DPNTEPsInfo> dpnTepsList,IpAddress extIp ) {
        logger.trace( " Delete Tunnels towards DC Gateway with Ip  {}", extIp ) ;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

            if (dpnTepsList == null || dpnTepsList.size() == 0) {
                logger.debug("no vtep to delete");
                return null ;
            }
            for( DPNTEPsInfo teps : dpnTepsList) {
                TunnelEndPoints firstEndPt = teps.getTunnelEndPoints().get(0) ;
                String interfaceName = firstEndPt.getInterfaceName() ;
                String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(interfaceName, firstEndPt.getIpAddress().getIpv4Address().getValue(), extIp.getIpv4Address().getValue()) ;
                InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
                t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);
            }
            futures.add(t.submit()) ;
        return futures ;
    }

}