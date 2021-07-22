package org.apache.nifi.snmp.operations;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.snmp.exception.InvalidFlowFileException;
import org.apache.nifi.snmp.utils.SNMPUtils;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;

import java.io.IOException;
import java.util.function.Supplier;

import static org.apache.nifi.snmp.operations.SNMPResourceHandler.INVALID_FLOWFILE_EXCEPTION_MESSAGE;

public class SendTrapSNMPHandler {
    private final SNMPResourceHandler snmpResourceHandler;
    private final Supplier<PDU> pduFactory;

    public SendTrapSNMPHandler(SNMPResourceHandler snmpResourceHandler, Supplier<PDU> pduFactory) {
        this.snmpResourceHandler = snmpResourceHandler;
        this.pduFactory = pduFactory;
    }

    public void sendTrap(final FlowFile flowFile) throws IOException {
        PDU pdu = pduFactory.get();
        Target target = snmpResourceHandler.getTarget();
        Snmp snmpManager = snmpResourceHandler.getSnmpManager();

        if (flowFile != null) {
            final boolean isAnyVariableAdded = SNMPUtils.addVariables(pdu, flowFile.getAttributes());
            if (!isAnyVariableAdded) {
                throw new InvalidFlowFileException(INVALID_FLOWFILE_EXCEPTION_MESSAGE);
            }
        }
        snmpManager.send(pdu, target);
    }
}
