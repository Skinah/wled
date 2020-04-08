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

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WLedDiscoveryService} Discovers and adds to the inbox any Wled devices found.
 *
 * @author Matthew Skinner - Initial contribution
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "binding.wled")
public class WLedDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(WLedDiscoveryService.class);

    public WLedDiscoveryService() {
        super(WLedHandler.SUPPORTED_THING_TYPES, 5, true);
    }

    private void newThingFound(String deviceID) {
        ThingTypeUID thingtypeuid = new ThingTypeUID(BINDING_ID, "wled");
        ThingUID thingUID = new ThingUID(thingtypeuid, WLedBrokerHandler.confirmedBridgeUID, deviceID);
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withBridge(WLedBrokerHandler.confirmedBridgeUID).withLabel("WLedString :" + deviceID).build();
        thingDiscovered(discoveryResult);
    }

    private MqttClient client;

    private void findThings() {

        try {
            client = new MqttClient(WLedBrokerHandler.confirmedAddress, MqttClient.generateClientId(),
                    new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();

            if (WLedBrokerHandler.confirmedUser != null && !WLedBrokerHandler.confirmedUser.contains("empty")) {
                options.setUserName(WLedBrokerHandler.confirmedUser);
            }

            if (WLedBrokerHandler.confirmedPassword != null && !WLedBrokerHandler.confirmedPassword.equals("empty")) {
                options.setPassword(WLedBrokerHandler.confirmedPassword.toCharArray());
            }

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {

                    if (topic.contains("wled/")) {
                        logger.debug("Discovery Service just recieved the following new wled controller:{}:{}", topic,
                                message);

                        String cutTopic = topic.replace("wled/", "");

                        int index = cutTopic.indexOf("/");
                        if (index != -1) // -1 means "not found"
                        {
                            String deviceID = (cutTopic.substring(0, index)); // Store the remote code for use later
                            newThingFound(deviceID);
                        }
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.connect(options);
            client.subscribe("wled/#", 1);

        } catch (MqttException e) {
            logger.error("Error: Could not connect to MQTT broker to search for New Things.{}", e);
        }
    }

    @Override
    protected void startScan() {
        removeOlderResults(getTimestampOfLastScan());

        if (WLedBrokerHandler.confirmedBridgeUID == null) {
            logger.info(
                    "No ONLINE WLed bridges were found. You need to add then edit a Bridge with your MQTT details before any of your globes can be found.");
            ThingTypeUID thingtypeuid = THING_TYPE_BROKER;
            ThingUID thingUID = new ThingUID(thingtypeuid, "Auto001");
            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withLabel("WLed")
                    .withThingType(THING_TYPE_BROKER).build();
            thingDiscovered(discoveryResult);
        }

        else if (!"empty".equals(WLedBrokerHandler.confirmedAddress)) {
            logger.info("WLedDiscoveryService is now looking for new things");
            findThings();

            try {
                Thread.sleep(3000);
                try {
                    client.disconnect();
                    deactivate();
                } catch (MqttException e) {

                }
            } catch (InterruptedException e) {

            }

        } else {
            logger.error(
                    "ERROR: Can not scan if no Bridges are setup with valid MQTT broker details. Setup an WLed bridge then try again.");
        }
    }

    @Override
    protected void startBackgroundDiscovery() {

    };

    @Override
    protected void deactivate() {
        super.deactivate();
    }
}
