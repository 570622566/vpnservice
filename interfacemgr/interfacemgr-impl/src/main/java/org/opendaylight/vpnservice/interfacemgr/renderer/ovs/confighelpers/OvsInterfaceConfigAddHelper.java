/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.idmanager.IdManager;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info.InterfaceParentEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007._interface.child.info._interface.parent.entry.InterfaceChildEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.BridgeEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge._interface.info.bridge.entry.BridgeInterfaceEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.meta.rev151007.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class OvsInterfaceConfigAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsInterfaceConfigAddHelper.class);

    public static List<ListenableFuture<Void>> addConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                                                Interface interfaceNew, IdManager idManager) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();

        IfTunnel ifTunnel = interfaceNew.getAugmentation(IfTunnel.class);
        if (ifTunnel != null) {
            addTunnelConfiguration(dataBroker, parentRefs, interfaceNew, idManager, futures);
            return futures;
        }

        addVlanConfiguration(interfaceNew, dataBroker, futures);
        return futures;
    }

    private static void addVlanConfiguration(Interface interfaceNew, DataBroker dataBroker,
                                             List<ListenableFuture<Void>> futures) {
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(interfaceNew.getName(), dataBroker);
        if (ifState == null) {
            return;
        }
        updateStateEntry(interfaceNew, t, ifState);

        IfL2vlan ifL2vlan = interfaceNew.getAugmentation(IfL2vlan.class);
        if (ifL2vlan == null || ifL2vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            return;
        }

        InterfaceParentEntryKey interfaceParentEntryKey = new InterfaceParentEntryKey(interfaceNew.getName());
        InterfaceParentEntry interfaceParentEntry =
                InterfaceMetaUtils.getInterfaceParentEntryFromConfigDS(interfaceParentEntryKey, dataBroker);
        if (interfaceParentEntry == null) {
            return;
        }

        List<InterfaceChildEntry> interfaceChildEntries = interfaceParentEntry.getInterfaceChildEntry();
        if (interfaceChildEntries == null) {
            return;
        }

        OperStatus operStatus = ifState.getOperStatus();
        PhysAddress physAddress = ifState.getPhysAddress();
        AdminStatus adminStatus = ifState.getAdminStatus();
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ifState.getLowerLayerIf().get(0));

        //FIXME: If the no. of child entries exceeds 100, perform txn updates in batches of 100.
        for (InterfaceChildEntry interfaceChildEntry : interfaceChildEntries) {
            InterfaceKey childIfKey = new InterfaceKey(interfaceChildEntry.getChildInterface());
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface ifaceChild =
                    InterfaceManagerCommonUtils.getInterfaceFromConfigDS(childIfKey, dataBroker);

            if (!ifaceChild.isEnabled()) {
                operStatus = OperStatus.Down;
            }

            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifChildStateId =
                    IfmUtil.buildStateInterfaceId(ifaceChild.getName());
            List<String> childLowerLayerIfList = new ArrayList<>();
            childLowerLayerIfList.add(0, nodeConnectorId.getValue());
            childLowerLayerIfList.add(1, interfaceNew.getName());
            InterfaceBuilder childIfaceBuilder = new InterfaceBuilder().setAdminStatus(adminStatus)
                    .setOperStatus(operStatus).setPhysAddress(physAddress).setLowerLayerIf(childLowerLayerIfList);
            childIfaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(ifaceChild.getName()));
            t.put(LogicalDatastoreType.OPERATIONAL, ifChildStateId, childIfaceBuilder.build(), true);
        }
        futures.add(t.submit());
    }

    private static void addTunnelConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                              Interface interfaceNew, IdManager idManager,
                                              List<ListenableFuture<Void>> futures) {
        LOG.debug("adding tunnel configuration for {}", interfaceNew.getName());
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        if (parentRefs == null) {
            LOG.warn("ParentRefs for interface: {} Not Found. " +
                    "Creation of Tunnel OF-Port not supported when dpid not provided.", interfaceNew.getName());
            return;
        }

        BigInteger dpId = parentRefs.getDatapathNodeIdentifier();
        if (dpId == null) {
            LOG.warn("dpid for interface: {} Not Found. No DPID provided. " +
                    "Creation of OF-Port not supported.", interfaceNew.getName());
            return;
        }

        createBridgeEntryIfNotPresent(dpId, dataBroker, t);

        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpId);
        BridgeInterfaceEntryKey bridgeInterfaceEntryKey = new BridgeInterfaceEntryKey(interfaceNew.getName());

        LOG.debug("creating bridge interfaceEntry in ConfigDS {}", bridgeEntryKey);
        InterfaceMetaUtils.createBridgeInterfaceEntryInConfigDS(bridgeEntryKey, bridgeInterfaceEntryKey,
                    interfaceNew.getName(), t);
        futures.add(t.submit());

        // create bridge on switch, if switch is connected
        BridgeRefEntryKey BridgeRefEntryKey = new BridgeRefEntryKey(dpId);
        InstanceIdentifier<BridgeRefEntry> dpnBridgeEntryIid =
                InterfaceMetaUtils.getBridgeRefEntryIdentifier(BridgeRefEntryKey);
        BridgeRefEntry bridgeRefEntry =
                InterfaceMetaUtils.getBridgeRefEntryFromOperDS(dpnBridgeEntryIid, dataBroker);
        if(bridgeRefEntry != null && bridgeRefEntry.getBridgeReference() != null) {
            LOG.debug("creating bridge interface on dpn {}", bridgeEntryKey);
            InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid =
                    (InstanceIdentifier<OvsdbBridgeAugmentation>) bridgeRefEntry.getBridgeReference().getValue();
            Optional<OvsdbBridgeAugmentation> bridgeNodeOptional =
                    IfmUtil.read(LogicalDatastoreType.OPERATIONAL, bridgeIid, dataBroker);
            if (bridgeNodeOptional.isPresent()) {
                OvsdbBridgeAugmentation ovsdbBridgeAugmentation = bridgeNodeOptional.get();
                String bridgeName = ovsdbBridgeAugmentation.getBridgeName().getValue();
                SouthboundUtils.addPortToBridge(bridgeIid, interfaceNew,
                        ovsdbBridgeAugmentation, bridgeName, interfaceNew.getName(), dataBroker, futures);
            }
        }
    }

    private static void updateStateEntry(Interface interfaceNew, WriteTransaction t,
                                         org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState) {
        OperStatus operStatus;
        if (!interfaceNew.isEnabled() && ifState.getOperStatus() != OperStatus.Down) {
            operStatus = OperStatus.Down;
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> ifStateId =
                    IfmUtil.buildStateInterfaceId(interfaceNew.getName());
            InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
            ifaceBuilder.setOperStatus(operStatus);
            ifaceBuilder.setKey(IfmUtil.getStateInterfaceKeyFromName(interfaceNew.getName()));
            t.merge(LogicalDatastoreType.OPERATIONAL, ifStateId, ifaceBuilder.build());
        }
    }

    private static void createBridgeEntryIfNotPresent(BigInteger dpId,
                                                      DataBroker dataBroker, WriteTransaction t) {
        LOG.debug("creating bridge entry if not present for dpn {}",dpId);
        BridgeEntryKey bridgeEntryKey = new BridgeEntryKey(dpId);
        InstanceIdentifier<BridgeEntry> bridgeEntryInstanceIdentifier =
                InterfaceMetaUtils.getBridgeEntryIdentifier(bridgeEntryKey);
        BridgeEntry bridgeEntry =
                InterfaceMetaUtils.getBridgeEntryFromConfigDS(bridgeEntryInstanceIdentifier,
                        dataBroker);
        if (bridgeEntry == null) {
            BridgeEntryBuilder bridgeEntryBuilder = new BridgeEntryBuilder().setKey(bridgeEntryKey)
                    .setDpid(dpId);
            t.put(LogicalDatastoreType.CONFIGURATION, bridgeEntryInstanceIdentifier, bridgeEntryBuilder.build(), true);
        }
    }
}