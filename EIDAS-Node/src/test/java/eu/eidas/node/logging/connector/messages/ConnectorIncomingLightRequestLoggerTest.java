/*
 * Copyright (c) 2019 by European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence
 */
package eu.eidas.node.logging.connector.messages;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import eu.eidas.auth.cache.ConcurrentMapJcacheServiceDefaultImpl;
import eu.eidas.auth.commons.BindingMethod;
import eu.eidas.auth.commons.EIDASValues;
import eu.eidas.auth.commons.EidasDigestUtil;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.EidasStringUtil;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.node.BeanProvider;
import eu.eidas.node.logging.MessageLoggerUtils;
import eu.eidas.node.utils.ReflectionUtils;
import eu.eidas.node.utils.logger.LevelFilter;
import eu.eidas.node.utils.logger.LoggerTestData;
import eu.eidas.specificcommunication.CommunicationCache;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import eu.eidas.specificcommunication.protocol.impl.SpecificConnectorCommunicationServiceExtensionImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.xml.security.utils.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.cache.Cache;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static eu.eidas.node.logging.AbstractLogger.END_TAG;
import static eu.eidas.node.logging.MessageLoggerTag.BLT_HASH;
import static eu.eidas.node.logging.MessageLoggerTag.DESTINATION;
import static eu.eidas.node.logging.MessageLoggerTag.FLOW_ID;
import static eu.eidas.node.logging.MessageLoggerTag.MSG_HASH;
import static eu.eidas.node.logging.MessageLoggerTag.MSG_ID;
import static eu.eidas.node.logging.MessageLoggerTag.NODE_ID;
import static eu.eidas.node.logging.MessageLoggerTag.OP_TYPE;
import static eu.eidas.node.logging.MessageLoggerTag.ORIGIN;
import static eu.eidas.node.logging.MessageLoggerTag.TIMESTAMP;
import static eu.eidas.node.utils.logger.LoggerTestUtils.createStartListAppender;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

/**
 * Tests for the {@link ConnectorIncomingLightRequestLogger}.
 *
 * @since 2.3
 */
public class ConnectorIncomingLightRequestLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ConnectorIncomingLightRequestLogger.class.getName());
    private ListAppender<ILoggingEvent> infoAppender;

    @Before
    public void setup() {
        infoAppender = createStartListAppender(new LevelFilter(Level.INFO));
        logger.addAppender(infoAppender);
    }

    @After
    public void teardown() {
        logger.detachAndStopAllAppenders();
    }

    /**
     * Test method for {@link ConnectorIncomingLightRequestLogger#logMessage(org.slf4j.Logger, HttpServletRequest)}.
     * Check no logging occurs when logging is deactivated for the logger
     * Must succeed.
     */
    @Test
    public void logMessageInactive () throws Exception {
        LoggerTestData testData = new LoggerTestData().
                setLogMessage(false).
                setDestination(StringUtils.EMPTY);

        ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger = connectorIncomingLightRequestLogger(testData);
        setUpBeanProvider(connectorIncomingLightRequestLogger);
        connectorIncomingLightRequestLogger.logMessage(logger, mockHttpServletRequest(testData, new String[]{testData.tokenBase64}));

        assertThat(infoAppender.list.size(), is(0));
    }

    /**
     * Test method for {@link ConnectorIncomingLightRequestLogger#logMessage(org.slf4j.Logger, HttpServletRequest)}.
     * Check logging occurs when logging is activated for the logger
     * Must succeed.
     */
    @Test
    public void logMessageActive () throws Exception {
        Cache<String, String> flowIdCache = new CommunicationCache(new ConcurrentMapJcacheServiceDefaultImpl());
        String tokenBase64 = EidasStringUtil.encodeToBase64(String.format("token_%s", randomAlphabetic(10)) .getBytes());

        LoggerTestData testData = new LoggerTestData().
                setLogMessage(true).
                setTokenBase64(tokenBase64).
                setFlowIdCache(flowIdCache).
                setOpType(EIDASValues.SPECIFIC_EIDAS_CONNECTOR_REQUEST.toString()).
                setNodeId("SpecificConnector").
                setOrigin("http://origin").
                setDestination("http://destination").
                setMsgId("msgID_" + randomAlphabetic(10));

        ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger = connectorIncomingLightRequestLogger(testData);
        setUpBeanProvider(connectorIncomingLightRequestLogger);

        String[] tokenBase64Array = {testData.tokenBase64};
        connectorIncomingLightRequestLogger.logMessage(logger, mockHttpServletRequest(testData, tokenBase64Array));

        assertThat(infoAppender.list.size(), is(1));
        String flowId = testData.flowIdCache.get(testData.msgId);
        assertTrue(StringUtils.isNotBlank(flowId));

        List<String> expectedStrings = Arrays.asList(
                TIMESTAMP.getValue(), END_TAG,
                OP_TYPE.getValue(), testData.opType, END_TAG,
                NODE_ID.getValue(), testData.nodeId, END_TAG,
                ORIGIN.getValue(), testData.origin, END_TAG,
                DESTINATION.getValue(), testData.destination, END_TAG,
                FLOW_ID.getValue(), flowId, END_TAG,
                MSG_ID.getValue(), END_TAG,
                MSG_HASH.getValue(), END_TAG,
                BLT_HASH.getValue()
        );

        String loggedMessage = infoAppender.list.get(0).getMessage();
        assertThat(loggedMessage, stringContainsInOrder(expectedStrings));
    }


    /**
     * Test method for {@link ConnectorIncomingLightRequestLogger#logMessage(org.slf4j.Logger, HttpServletRequest)}.
     * when in the {@link HttpServletRequest} instance for the key {EidasParameterKeys.SAML_RESPONSE} two tokens are available
     * the first one the bad token: not to be processed
     * the second and last one the good token: the one to be logged
     *
     * Must succeed.
     */
    @Test
    public void testTwoTokensHttpRequestGoodTokenLast() throws Exception {
        Cache<String, String> flowIdCache = new CommunicationCache(new ConcurrentMapJcacheServiceDefaultImpl());
        String tokenBase64 = EidasStringUtil.encodeToBase64(String.format("token_%s", randomAlphabetic(10)) .getBytes());

        LoggerTestData testData = new LoggerTestData().
                setLogMessage(true).
                setTokenBase64(tokenBase64).
                setFlowIdCache(flowIdCache).
                setOpType(EIDASValues.SPECIFIC_EIDAS_CONNECTOR_REQUEST.toString()).
                setNodeId("SpecificConnector").
                setOrigin("http://origin").
                setDestination("http://destination").
                setMsgId("msgID_" + randomAlphabetic(10));

        ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger = connectorIncomingLightRequestLogger(testData);
        setUpBeanProvider(connectorIncomingLightRequestLogger);

        String goodToken = "goodToken";
        String badToken = "badToken";
        String[] tokenBase64Array = {badToken, goodToken};
        connectorIncomingLightRequestLogger.logMessage(logger, mockHttpServletRequest(testData, tokenBase64Array));

        assertThat(infoAppender.list.size(), is(1));
        String flowId = testData.flowIdCache.get(testData.msgId);
        assertTrue(StringUtils.isNotBlank(flowId));

        String loggedMessage = infoAppender.list.get(0).getMessage();

        byte[] bytes = EidasStringUtil.decodeBytesFromBase64(goodToken);
        byte[] msgHashBytes = EidasDigestUtil.hashPersonalToken(bytes);
        String s = Base64.encode(msgHashBytes, 88);
        List<String> strings = Arrays.asList(s);
        assertThat(loggedMessage, stringContainsInOrder(strings));
    }

    private ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger (LoggerTestData testData) throws SpecificCommunicationException {
        ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger = new ConnectorIncomingLightRequestLogger();
        connectorIncomingLightRequestLogger.setMessageLoggerUtils(mockMessageLoggerUtils(testData));
        connectorIncomingLightRequestLogger.setSpringManagedSpecificConnectorCommunicationService(mockSpecificCommunicationService(testData));
        connectorIncomingLightRequestLogger.setSpringManagedSpecificConnectorCommunicationServiceExtension(mockSpecificCommunicationServiceExtension(testData));
        connectorIncomingLightRequestLogger.setFlowIdCache(testData.flowIdCache);
        return connectorIncomingLightRequestLogger;
    }

    private void setUpBeanProvider (ConnectorIncomingLightRequestLogger connectorIncomingLightRequestLogger) {
        ApplicationContext mockApplicationContext = mock(ApplicationContext.class);
        ReflectionUtils.setStaticField(BeanProvider.class, "CONTEXT", mockApplicationContext);
        Mockito.when(mockApplicationContext.getBean(anyString())).thenReturn(connectorIncomingLightRequestLogger);
    }

    private HttpServletRequest mockHttpServletRequest(LoggerTestData testData, String[] tokenBase64Array) {
        HttpServletRequest mockHttpServletRequest = mock(HttpServletRequest.class);
        Mockito.when(mockHttpServletRequest.getRequestURL()).thenReturn(new StringBuffer(testData.destination));
        Mockito.when(mockHttpServletRequest.getHeader(EIDASValues.REFERER.toString())).thenReturn(testData.origin);

        HashMap<String, String[]> httpParametersMap = createHttpParameterMap(tokenBase64Array);
        Mockito.when(mockHttpServletRequest.getParameterMap()).thenReturn(httpParametersMap);
        Mockito.when(mockHttpServletRequest.getMethod()).thenReturn(BindingMethod.POST.getValue());

        return mockHttpServletRequest;
    }

    private HashMap<String, String[]> createHttpParameterMap(String[] tokenBase64Array) {
        String servletParameter = EidasParameterKeys.TOKEN.toString();
        HashMap<String, String[]> httpParametersMap = new HashMap<>();
        httpParametersMap.put(servletParameter, tokenBase64Array);
        return httpParametersMap;
    }

    private MessageLoggerUtils mockMessageLoggerUtils(LoggerTestData testData) {
        MessageLoggerUtils mockMessageLoggerUtils = mock(MessageLoggerUtils.class);
        Mockito.when(mockMessageLoggerUtils.isLogMessages()).thenReturn(testData.logMessage);
        return mockMessageLoggerUtils;
    }

    private ILightRequest mockLightRequest (LoggerTestData  testData) {
        ILightRequest mockLightRequest = mock(ILightRequest.class);
        Mockito.when(mockLightRequest.getId()).thenReturn(testData.msgId);
        return mockLightRequest;
    }

    private SpecificCommunicationService mockSpecificCommunicationService(LoggerTestData testData) throws SpecificCommunicationException {
        ILightRequest mockLightRequest = mockLightRequest(testData);
        SpecificCommunicationService mockSpecificCommunicationService = mock(SpecificCommunicationService.class);
        Mockito.when(mockSpecificCommunicationService.getAndRemoveRequest(anyString(), anyList())).thenReturn(mockLightRequest);
        return mockSpecificCommunicationService;
    }

    private SpecificConnectorCommunicationServiceExtensionImpl mockSpecificCommunicationServiceExtension(LoggerTestData testData) {
        SpecificConnectorCommunicationServiceExtensionImpl mock = mock(SpecificConnectorCommunicationServiceExtensionImpl.class);
        Mockito.when(mock.getLightTokenRequestNodeId()).thenReturn(testData.nodeId);
        return mock;
    }
}