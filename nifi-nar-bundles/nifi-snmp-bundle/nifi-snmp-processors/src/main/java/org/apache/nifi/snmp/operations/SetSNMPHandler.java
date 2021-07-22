package org.apache.nifi.snmp.operations;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.snmp.dto.SNMPSingleResponse;
import org.apache.nifi.snmp.exception.InvalidFlowFileException;
import org.apache.nifi.snmp.exception.RequestTimeoutException;
import org.apache.nifi.snmp.utils.SNMPUtils;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.PDUFactory;

import java.io.IOException;

import static org.apache.nifi.snmp.operations.SNMPResourceHandler.INVALID_FLOWFILE_EXCEPTION_MESSAGE;
import static org.apache.nifi.snmp.operations.SNMPResourceHandler.REQUEST_TIMEOUT_EXCEPTION_TEMPLATE;

public class SetSNMPHandler {
    private static final PDUFactory setPduFactory = new DefaultPDUFactory(PDU.SET);

    private final SNMPResourceHandler snmpResourceHandler;

    public SetSNMPHandler(SNMPResourceHandler snmpResourceHandler) {
        this.snmpResourceHandler = snmpResourceHandler;
    }

    /**
     * Executes the SNMP set request and returns the response.
     *
     * @param flowFile FlowFile which contains variables for the PDU
     * @return Response event
     * @throws IOException IO Exception
     */
    public SNMPSingleResponse set(final FlowFile flowFile) throws IOException {
        Target target = snmpResourceHandler.getTarget();
        Snmp snmpManager = snmpResourceHandler.getSnmpManager();

        final PDU pdu = setPduFactory.createPDU(target);
        if (SNMPUtils.addVariables(pdu, flowFile.getAttributes())) {
            final ResponseEvent response = snmpManager.set(pdu, target);
            final PDU responsePdu = response.getResponse();
            if (responsePdu == null) {
                throw new RequestTimeoutException(String.format(REQUEST_TIMEOUT_EXCEPTION_TEMPLATE, "write"));
            }
            return new SNMPSingleResponse(target, responsePdu);
        }
        throw new InvalidFlowFileException(INVALID_FLOWFILE_EXCEPTION_MESSAGE);
    }
}
