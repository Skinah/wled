/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.wled.internal;

import static org.openhab.binding.wled.internal.WLedBindingConstants.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WLedBrokerHandler} Talks to the MQTT broker and updates the Openhab controls from mqtt messages.
 *
 * @author Matthew Skinner - Initial contribution
 */
public class WLedBrokerHandler extends BaseBridgeHandler implements MqttCallbackExtended {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_BROKER);
    private final ScheduledExecutorService checkConnection = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> checkConnectionJob = null;
    private final Logger logger = LoggerFactory.getLogger(WLedBrokerHandler.class);

    public static String confirmedAddress = "empty";
    public static String confirmedUser = "empty";
    public static String confirmedPassword = "empty";
    public static ThingUID confirmedBridgeUID;
    public static boolean triggerRefresh = true;

    private final ScheduledExecutorService schedulerOut = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sendQueuedMQTTTimerJob = null;
    private final ScheduledExecutorService schedulerIn = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> processIncommingMQTTTimerJob = null;

    private LinkedList<String> fifoOutgoingTopic = new LinkedList<String>();
    private LinkedList<String> fifoOutgoingPayload = new LinkedList<String>();
    public ReentrantLock lockOutGoing = new ReentrantLock();
    private LinkedList<String> fifoIncommingTopic = new LinkedList<String>();
    private LinkedList<String> fifoIncommingPayload = new LinkedList<String>();
    public ReentrantLock lockInComming = new ReentrantLock();
    BigDecimal brightness = new BigDecimal(0);

    private MqttClient client;
    private Configuration bridgeConfig;
    private boolean increaseChoke = false;
    WLedBrokerHandler childHandler;

    static private String resolveXML(String messageXML, String xmlToFetch, int resultMaxLength) {
        String result = "";
        int index = 0;
        index = messageXML.indexOf(xmlToFetch);
        if (index != -1) // It was found as -1 means "not found"
        {
            if ((index + xmlToFetch.length() + resultMaxLength) > messageXML.length()) {
                result = (messageXML.substring(index + xmlToFetch.length(), messageXML.length()));
            } else {
                result = (messageXML.substring(index + xmlToFetch.length(),
                        index + xmlToFetch.length() + resultMaxLength));
            }
            index = result.indexOf('<'); // need to be careful, only matches first bad char found//
            if (index == -1)// , not found so make second check
            {
                index = result.indexOf('"');
                if (index == -1)// " not found so it passed both checks
                {
                    index = result.indexOf('}');
                    if (index == -1)// } not found so it passed all 3 checks
                    {
                        return result;
                    } else {
                        return result.substring(0, index); // Strip off the } as it is the only bad char.
                    }
                } else { // no , but it found a ", have not checked for } as have not seen that occur yet.
                    return result.substring(0, index); // Strip off the " as it is a bad char, careful as } may still be
                                                       // in string.
                }
            } else { // Found a "," , now we need to check for " in case both are in string.

                result = result.substring(0, index); // Strip off any left over char.
                index = result.indexOf('"');
                if (index == -1)// " not found so it passed both checks
                {
                    return result;
                } else {
                    return result.substring(0, index); // Strip off any left over char.
                }
            }
        }
        return "";
    }

    private void processIncomingState(String deviceID, String currentTopic, String messageJSON) {
        String channelPrefix = "wled:wled:" + thing.getUID().getId() + ":" + deviceID + ":";

        if (logger.isDebugEnabled()) {
            logger.debug("Processing new incoming MQTT message to update Openhab's controls.");
            logger.debug("Message\t={}", messageJSON);
            logger.debug("deviceID\t={}", deviceID);
            logger.debug("Chan Prefix\t={}", channelPrefix);
        }

        switch (currentTopic) {
            case "v":
                if (messageJSON.contains("<cy>1</cy>")) {
                    updateState(new ChannelUID(channelPrefix + CHANNEL_PRESET_CYCLE), OnOffType.valueOf("ON"));
                } else {
                    updateState(new ChannelUID(channelPrefix + CHANNEL_PRESET_CYCLE), OnOffType.valueOf("OFF"));
                }
                if (messageJSON.contains("<nl>1</nl>")) {
                    updateState(new ChannelUID(channelPrefix + CHANNEL_SLEEP), OnOffType.valueOf("ON"));
                } else {
                    updateState(new ChannelUID(channelPrefix + CHANNEL_SLEEP), OnOffType.valueOf("OFF"));
                }
                if (messageJSON.contains("<fx>")) {
                    int itmp = Integer.parseInt(resolveXML(messageJSON, "<fx>", 3));
                    updateState(new ChannelUID(channelPrefix + CHANNEL_FX), new DecimalType(itmp));
                }
                if (messageJSON.contains("<fp>")) {
                    int itmp = Integer.parseInt(resolveXML(messageJSON, "<fp>", 3));
                    updateState(new ChannelUID(channelPrefix + CHANNEL_PALETTES), new DecimalType(itmp));
                }
                break;
            case "c":
                int rgb = Integer.parseInt(messageJSON.substring(1), 16);
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = (rgb >>> 0) & 0xFF;
                updateState(new ChannelUID(channelPrefix + CHANNEL_COLOUR),
                        new HSBType(HSBType.fromRGB(r, g, b).getHue() + "," + HSBType.fromRGB(r, g, b).getSaturation()
                                + "," + brightness));
                // updateState(new ChannelUID(channelPrefix + CHANNEL_COLOUR), HSBType.fromRGB(r, g, b));
                break;
            case "g":
                if (messageJSON.contentEquals("0")) {
                    brightness = new BigDecimal(0);
                    updateState(new ChannelUID(channelPrefix + CHANNEL_COLOUR), OnOffType.valueOf("OFF"));
                } else {
                    brightness = new BigDecimal(messageJSON);
                    brightness = brightness.divide(new BigDecimal(2.55), RoundingMode.HALF_UP);
                    updateState(new ChannelUID(channelPrefix + CHANNEL_COLOUR), new PercentType(brightness.intValue()));
                }
                break;
        }
    }

    Runnable pollingIncommingQueuedMQTT = new Runnable() {
        @Override
        public void run() {
            if (thing.getStatus() == ThingStatus.OFFLINE) {
                // keeps the queue ready until it comes back online to process//
                return;
            } else if (!fifoIncommingTopic.isEmpty()) {
                lockInComming.lock();
                String topic, payload;
                try {
                    topic = fifoIncommingTopic.removeFirst();
                    payload = fifoIncommingPayload.removeFirst();
                } finally {
                    lockInComming.unlock();
                }
                String cutTopic = topic.replace("wled/", "");
                int index = cutTopic.indexOf("/");
                if (index != -1) // -1 means "not found"
                {
                    String deviceID = (cutTopic.substring(0, index));
                    cutTopic = topic.replace("wled/" + deviceID + "/", "");
                    String currentTopic = (cutTopic.substring(0, cutTopic.length()));
                    processIncomingState(deviceID, currentTopic, payload);
                }
            } else if (!processIncommingMQTTTimerJob.isCancelled()) {
                processIncommingMQTTTimerJob.cancel(true);
                if (processIncommingMQTTTimerJob.isCancelled()) {
                    processIncommingMQTTTimerJob = null;
                }
            }
        }
    };

    @Override
    public void messageArrived(String topic, MqttMessage payload) throws Exception {
        logger.debug("* Recieved the following new WLed state:{} : {}", topic, payload.toString());
        lockInComming.lock();
        try {
            fifoIncommingTopic.addLast(topic);
            fifoIncommingPayload.addLast(payload.toString());
        } finally {
            lockInComming.unlock();
        }
        if (processIncommingMQTTTimerJob == null) {
            processIncommingMQTTTimerJob = schedulerIn.scheduleWithFixedDelay(pollingIncommingQueuedMQTT, 10, 10,
                    TimeUnit.MILLISECONDS);
        }
    }

    public void subscribeToMQTT() {
        try {
            client.subscribe("wled/#", 1);
            logger.info("Sucessfully subscribed to wled/#");
        } catch (MqttException e) {
            logger.error("Error: Could not subscribe to 'wled/#' cause is:{}", e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, java.lang.String serverURI) {
        // logger.info("Sucessfully connected to the MQTT broker.");
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT connection has been lost, cause reported is:{}", cause);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "MQTT broker connection lost:" + cause);
    }

    public boolean connectMQTT(boolean useCleanSession) {

        try {
            client = new MqttClient(bridgeConfig.get(CONFIG_MQTT_ADDRESS).toString(),
                    "wled:" + this.getThing().getUID().getId().toString(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(useCleanSession);
            if (bridgeConfig.get(CONFIG_MQTT_USER_NAME) != null) {
                options.setUserName(bridgeConfig.get(CONFIG_MQTT_USER_NAME).toString());
                confirmedUser = bridgeConfig.get(CONFIG_MQTT_USER_NAME).toString();
            }
            if (bridgeConfig.get(CONFIG_MQTT_PASSWORD) != null) {
                options.setPassword(bridgeConfig.get(CONFIG_MQTT_PASSWORD).toString().toCharArray());
                confirmedPassword = bridgeConfig.get(CONFIG_MQTT_PASSWORD).toString();
            }
            options.setMaxInflight(30); // up to 30 messages at once can be sent without a token back
            options.setAutomaticReconnect(true);
            options.setKeepAliveInterval(20);
            options.setConnectionTimeout(20); // connection must be made in under 20 seconds
            client.setCallback(this);
            client.connect(options);
        } catch (MqttException e) {
            logger.error("Error: Could not connect to MQTT broker.{}", e);
            client = null;
            return false;
        }
        confirmedAddress = bridgeConfig.get(CONFIG_MQTT_ADDRESS).toString();
        updateStatus(ThingStatus.ONLINE);
        recordBridgeID();
        return true;
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    private void sendMQTT(String topic, String payload) {
        try {
            if (client.isConnected()) {
                client.publish(topic, // topic
                        payload.getBytes(), // payload
                        1, // QoS of 1 will garrantee the message gets through without the extra overheads of 2
                        false); // Not retained
            }
        } catch (MqttPersistenceException e) {
            logger.error("Error: Could not connect/send to MQTT broker:{}", e);
        } catch (MqttException e) {
            logger.error("Error: Could not connect/send to MQTT broker:{}", e);
        }
    }

    Runnable pollingSendQueuedMQTT = new Runnable() {
        @Override
        public void run() {
            if (client.isConnected()) {
                if (fifoOutgoingTopic.size() >= 1) {
                    lockOutGoing.lock();
                    try {
                        sendMQTT(fifoOutgoingTopic.removeFirst(), fifoOutgoingPayload.removeFirst());
                        logger.trace("MQTT message just sent, there are now {} more messages in the queue",
                                fifoOutgoingTopic.size());
                    } catch (NoSuchElementException e) {
                        logger.warn(
                                "!!! Outgoing MQTT queue *CATCH* Triggered. Wiping the outgoing FIFO buffer clean !!!");
                        fifoOutgoingTopic.clear();
                        fifoOutgoingPayload.clear();
                    } finally {
                        lockOutGoing.unlock();
                    }
                } else {
                    sendQueuedMQTTTimerJob.cancel(true);
                    sendQueuedMQTTTimerJob = null;
                    return;
                }
            }
        }
    };

    public void queueToSendMQTT(String topic, String payload) {

        if (topic == null || payload == null) {
            logger.error("null was found in requested outgoing message:{}:{}:", topic, payload);
            return;
        }

        try {
            if (fifoOutgoingTopic.size() > 1 && fifoOutgoingTopic.getLast().equals(topic)) {
                lockOutGoing.lock();
                try {
                    logger.debug("Message reduction has removed a MQTT message.");
                    fifoOutgoingTopic.removeLast();
                    fifoOutgoingPayload.removeLast();
                } finally {
                    lockOutGoing.unlock();
                }
                if (increaseChoke == false) {
                    increaseChoke = true;
                    logger.debug("changing queue to DELAY_BETWEEN_SAME_GLOBE speed.");
                    if (sendQueuedMQTTTimerJob != null) {
                        sendQueuedMQTTTimerJob.cancel(false);
                    }
                    sendQueuedMQTTTimerJob = schedulerOut.scheduleWithFixedDelay(pollingSendQueuedMQTT,
                            Integer.parseInt(bridgeConfig.get(CONFIG_DELAY_BETWEEN_SAME_GLOBE).toString()),
                            Integer.parseInt(bridgeConfig.get(CONFIG_DELAY_BETWEEN_SAME_GLOBE).toString()),
                            TimeUnit.MILLISECONDS);
                }
                logger.debug(
                        "Message reduction has removed a command as the queue contains multiples for the same globe.");
            } else if (increaseChoke == true) {
                increaseChoke = false;
                logger.debug("changing queue back to normal speed.");
                if (sendQueuedMQTTTimerJob != null) {
                    sendQueuedMQTTTimerJob.cancel(false);
                }
                sendQueuedMQTTTimerJob = schedulerOut.scheduleWithFixedDelay(pollingSendQueuedMQTT,
                        Integer.parseInt(bridgeConfig.get(CONFIG_DELAY_BETWEEN_MQTT).toString()),
                        Integer.parseInt(bridgeConfig.get(CONFIG_DELAY_BETWEEN_MQTT).toString()),
                        TimeUnit.MILLISECONDS);
            }
            lockOutGoing.lock();
            try {
                fifoOutgoingTopic.addLast(topic);
                fifoOutgoingPayload.addLast(payload);
            } finally {
                lockOutGoing.unlock();
            }
        } catch (NoSuchElementException e) {
            logger.info(
                    "!!!! queueToSend *CATCH* Triggered, wiping the outgoing FIFO buffer clean and trying to resend ********************");
            fifoOutgoingTopic.clear();
            fifoOutgoingPayload.clear();
            fifoOutgoingTopic.addLast(topic);
            fifoOutgoingPayload.addLast(payload);
        }

        if (sendQueuedMQTTTimerJob == null) {
            sendQueuedMQTTTimerJob = schedulerOut.scheduleWithFixedDelay(pollingSendQueuedMQTT, 0,
                    Integer.parseInt(bridgeConfig.get(CONFIG_DELAY_BETWEEN_MQTT).toString()), TimeUnit.MILLISECONDS);
            logger.debug("Started timer because it was null.");
        }
    }

    public void disconnectMQTT() {
        try {
            client.disconnect();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Currently disconnected from the MQTT broker.");
            // wait needed to fix issue when trying to reconnect too fast after a disconnect.
            // Thread.sleep(3000);
        } catch (MqttException e) {
            logger.error("Could not disconnect from MQTT broker.{}", e);
        }
    }

    public WLedBrokerHandler(Bridge thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

    }

    private void recordBridgeID() {
        confirmedBridgeUID = this.getThing().getUID();
    }

    Runnable pollConnection = new Runnable() {
        @Override
        public void run() {
            if (thing.getStatus() == ThingStatus.ONLINE) {
                if (triggerRefresh) {
                    triggerRefresh = false;
                    subscribeToMQTT();
                }
            } else if (client == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Could not connect to the MQTT broker, check the address, user and pasword are correct and the broker is online.");
                connectMQTT(false);
            }
        }
    };

    @Override
    public void initialize() {
        bridgeConfig = thing.getConfiguration();
        checkConnectionJob = checkConnection.scheduleWithFixedDelay(pollConnection, 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        if (client != null) {
            disconnectMQTT();
        }
        if (sendQueuedMQTTTimerJob != null) {
            sendQueuedMQTTTimerJob.cancel(false);
            sendQueuedMQTTTimerJob.cancel(true);
            sendQueuedMQTTTimerJob = null;
        }
        if (processIncommingMQTTTimerJob != null) {
            processIncommingMQTTTimerJob.cancel(false);
            processIncommingMQTTTimerJob.cancel(true);
            processIncommingMQTTTimerJob = null;
        }
        if (checkConnectionJob != null) {
            checkConnectionJob.cancel(false);
            checkConnectionJob.cancel(true);
            checkConnectionJob = null;
        }
    }
}
