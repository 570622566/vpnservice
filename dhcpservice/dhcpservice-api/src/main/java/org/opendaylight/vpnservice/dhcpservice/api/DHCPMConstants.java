/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.dhcpservice.api;

import java.math.BigInteger;

public final class DHCPMConstants {

    public static final long DHCP_TABLE_MAX_ENTRY = 10000;

    public static final int DEFAULT_DHCP_FLOW_PRIORITY = 50;
    public static final int ARP_FLOW_PRIORITY = 50;

    public static final BigInteger COOKIE_DHCP_BASE = new BigInteger("6800000", 16);
    public static final BigInteger METADATA_ALL_CLEAR_MASK = new BigInteger("0000000000000000", 16);
    public static final BigInteger METADATA_ALL_SET_MASK = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    public static final String FLOWID_PREFIX = "DHCP.";
    public static final String VMFLOWID_PREFIX = "DHCP.INTERFACE.";
    public static final String BCAST_DEST_IP = "255.255.255.255";
    public static final int BCAST_IP = 0xffffffff;

    public static final short dhcpClientPort = 68;
    public static final short dhcpServerPort = 67;

    public static final int DEFAULT_LEASE_TIME = 86400;
    public static final String DEFAULT_DOMAIN_NAME = "openstacklocal";

    public static final BigInteger INVALID_DPID = new BigInteger("-1");
}
