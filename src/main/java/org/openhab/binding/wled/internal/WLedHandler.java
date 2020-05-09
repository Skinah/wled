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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WLedHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Matthew Skinner - Initial contribution
 */

public class WLedHandler extends BaseThingHandler {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<ThingTypeUID>(
            Arrays.asList(THING_TYPE_WLED));
    private String deviceID = this.getThing().getUID().getId();// eg 0x014
    private final Logger logger = LoggerFactory.getLogger(WLedHandler.class);
    private WLedBrokerHandler bridgeHandler;
    BigDecimal brightness = new BigDecimal(0);
    @SuppressWarnings("unused")
    private Configuration config;

    public WLedHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command.toString() == "REFRESH") {
            logger.trace("'REFRESH' command has been called for:{}", channelUID);
            // This will cause all retained messages to be resent.
            WLedBrokerHandler.triggerRefresh = true;
            return;
        }

        String topic = "wled/" + deviceID;

        switch (channelUID.getId()) {
            case CHANNEL_COLOUR:
                if ("ON".equals(command.toString())) {
                    bridgeHandler.queueToSendMQTT(topic, command.toString());
                    break;
                } else if ("0".equals(command.toString()) || "OFF".equals(command.toString())) {
                    bridgeHandler.queueToSendMQTT(topic, command.toString());
                    brightness = new BigDecimal(0);
                    break;
                } else if (command instanceof HSBType) {
                    HSBType hsb = new HSBType(command.toString());
                    String hex = Integer.toHexString(hsb.getRGB() & 0xffffff);
                    while (hex.length() < 6) {
                        hex = "0" + hex;
                    }
                    hex = "#" + hex;
                    bridgeHandler.queueToSendMQTT(topic + "/col", hex);
                    brightness = new BigDecimal(hsb.getBrightness().toString());
                    brightness = brightness.multiply(new BigDecimal(2.55));
                    break;
                } else if (command instanceof IncreaseDecreaseType) {
                    if ("INCREASE".equals(command.toString())) {
                        brightness = brightness.add(new BigDecimal(25.5));
                        if (brightness.intValue() > 255) {
                            brightness = new BigDecimal(255);
                        }
                        bridgeHandler.queueToSendMQTT(topic, "" + brightness.intValue());
                    } else {
                        brightness = brightness.subtract(new BigDecimal(25.5));
                        if (brightness.intValue() < 0) {
                            brightness = new BigDecimal(0);
                        }
                        bridgeHandler.queueToSendMQTT(topic, "" + brightness.intValue());
                    }
                    break;
                }
                // this is here for when the command is Percentype and not HSBtype//
                brightness = new BigDecimal(command.toString());
                brightness = brightness.multiply(new BigDecimal(2.55));

                // dtmp = (int) (dtmp * 2.55);
                bridgeHandler.queueToSendMQTT(topic, "" + brightness.intValue());
                break;
            case CHANNEL_PALETTES:
                bridgeHandler.queueToSendMQTT(topic + "/api", "FP=" + command.toString());
                break;
            case CHANNEL_FX:
                bridgeHandler.queueToSendMQTT(topic + "/api", "FX=" + command.toString());
                break;
            case CHANNEL_SPEED:
                double dtmp = 0;
                if ("OFF".equals(command.toString())) {
                    dtmp = 0;
                } else if ("ON".equals(command.toString())) {
                    dtmp = 255;
                } else {
                    dtmp = Double.parseDouble(command.toString());
                    dtmp = (int) (dtmp * 2.55);
                }
                bridgeHandler.queueToSendMQTT(topic + "/api", "SX=" + dtmp);
                break;
            case CHANNEL_INTENSITY:
                if ("OFF".equals(command.toString())) {
                    dtmp = 0;
                } else if ("ON".equals(command.toString())) {
                    dtmp = 255;
                } else {
                    dtmp = Double.parseDouble(command.toString());
                    dtmp = (int) (dtmp * 2.55);
                }
                bridgeHandler.queueToSendMQTT(topic + "/api", "IX=" + dtmp);
                break;
            case CHANNEL_SLEEP:
                if ("ON".equals(command.toString())) {
                    bridgeHandler.queueToSendMQTT(topic + "/api", "ND");
                } else {
                    bridgeHandler.queueToSendMQTT(topic + "/api", "NL=0");
                }
                break;
            case CHANNEL_PRESETS:
                bridgeHandler.queueToSendMQTT(topic + "/api", "PL=" + command.toString());
                break;
            case CHANNEL_PRESET_DURATION:
                if ("OFF".equals(command.toString())) {
                    dtmp = 0;
                } else if ("ON".equals(command.toString())) {
                    dtmp = 255;
                } else {
                    dtmp = Double.parseDouble(command.toString());
                    dtmp = (dtmp * 600) + 500; // scale from 0.5 seconds to 1 minute
                }
                bridgeHandler.queueToSendMQTT(topic + "/api", "PT=" + dtmp);
                break;
            case CHANNEL_PRESET_TRANS_TIME:
                if ("OFF".equals(command.toString())) {
                    dtmp = 0;
                } else if ("ON".equals(command.toString())) {
                    dtmp = 255;
                } else {
                    dtmp = Double.parseDouble(command.toString());
                    dtmp = (dtmp * 600) + 500; // scale from 0.5 seconds to 1 minute
                }
                bridgeHandler.queueToSendMQTT(topic + "/api", "TT=" + dtmp);
                break;
            case CHANNEL_PRESET_CYCLE:
                if ("ON".equals(command.toString())) {
                    bridgeHandler.queueToSendMQTT(topic + "/api", "CY=1");
                } else {
                    bridgeHandler.queueToSendMQTT(topic + "/api", "CY=0");
                }
                break;

        } // end switch

    }

    @Override
    public void initialize() {
        if (getBridge() == null) {
            logger.error("This globe {} does not have a bridge selected, please fix.", deviceID);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Globe must have a valid bridge selected to be able to come online, check you have a bridge selected.");
        } else {
            updateStatus(ThingStatus.ONLINE);
            deviceID = this.getThing().getUID().getId();// eg 0x014
            config = getThing().getConfiguration();
            if (getBridge().getHandler() != null) {
                bridgeHandler = (WLedBrokerHandler) getBridge().getHandler();
            } else {
                logger.error("bridgeHandler is null");
                logger.error("This globe {} does not have a bridge selected, please fix.", deviceID);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Globe must have a valid bridge selected to be able to come online, check you have a bridge selected.");
            }
        }
    }
}
