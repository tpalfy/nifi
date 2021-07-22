package org.apache.nifi.snmp.operations;

import org.apache.nifi.snmp.dto.SNMPSingleResponse;
import org.apache.nifi.snmp.dto.SNMPTreeResponse;
import org.apache.nifi.snmp.exception.RequestTimeoutException;
import org.apache.nifi.snmp.exception.SNMPWalkException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.PDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.io.IOException;
import java.util.List;

import static org.apache.nifi.snmp.operations.SNMPResourceHandler.REQUEST_TIMEOUT_EXCEPTION_TEMPLATE;

public class GetSNMPHandler {
    private static final PDUFactory getPduFactory = new DefaultPDUFactory(PDU.GET);

    private final SNMPResourceHandler snmpResourceHandler;

    public GetSNMPHandler(SNMPResourceHandler snmpResourceHandler) {
        this.snmpResourceHandler = snmpResourceHandler;
    }

    /**
     * Construct the PDU to perform the SNMP Get request and returns
     * the result in order to create the flow file.
     *
     * @return {@link ResponseEvent}
     */
    public SNMPSingleResponse get(final String oid) throws IOException {
        Target target = snmpResourceHandler.getTarget();
        Snmp snmpManager = snmpResourceHandler.getSnmpManager();

        final PDU pdu = getPduFactory.createPDU(target);
        pdu.add(new VariableBinding(new OID(oid)));
        final ResponseEvent response = snmpManager.get(pdu, target);
        final PDU responsePdu = response.getResponse();
        if (responsePdu == null) {
            throw new RequestTimeoutException(String.format(REQUEST_TIMEOUT_EXCEPTION_TEMPLATE, "read"));
        }
        return new SNMPSingleResponse(target, responsePdu);
    }

    /**
     * Perform a SNMP walk and returns the list of {@link TreeEvent}
     *
     * @return the list of {@link TreeEvent}
     */
    public SNMPTreeResponse walk(final String oid) {
        Target target = snmpResourceHandler.getTarget();
        Snmp snmpManager = snmpResourceHandler.getSnmpManager();

        final TreeUtils treeUtils = new TreeUtils(snmpManager, getPduFactory);
        final List<TreeEvent> subtree = treeUtils.getSubtree(target, new OID(oid));
        if (subtree.isEmpty()) {
            throw new SNMPWalkException(String.format("The subtree associated with the specified OID %s is empty.", oid));
        }
        if (isSnmpError(subtree)) {
            throw new SNMPWalkException("Agent is not available, OID not found or user not found. Please, check if (1) the " +
                "agent is available, (2) the processor's SNMP version matches the agent version, (3) the OID is " +
                "correct, (4) The user is valid.");
        }
        if (isLeafElement(subtree)) {
            throw new SNMPWalkException(String.format("OID not found or it is a single leaf element. The leaf element " +
                "associated with this %s OID does not contain child OIDs. Please check if the OID exists in the agent " +
                "MIB or specify a parent OID with at least one child element", oid));
        }

        return new SNMPTreeResponse(target, subtree);
    }

    private boolean isSnmpError(final List<TreeEvent> subtree) {
        return subtree.size() == 1 && subtree.get(0).getVariableBindings() == null;
    }

    private boolean isLeafElement(final List<TreeEvent> subtree) {
        return subtree.size() == 1 && subtree.get(0).getVariableBindings().length == 0;
    }
}
