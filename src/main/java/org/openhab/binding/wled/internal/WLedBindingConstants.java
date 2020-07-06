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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link WLedBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Matthew Skinner - Initial contribution
 */
@NonNullByDefault
public class WLedBindingConstants {

    public static final String BINDING_ID = "wled";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_BROKER = new ThingTypeUID(BINDING_ID, "mqttBroker");
    public static final ThingTypeUID THING_TYPE_WLED = new ThingTypeUID(BINDING_ID, "wled");

    // Broker config//
    public static final String CONFIG_MQTT_ADDRESS = "ADDR";
    public static final String CONFIG_MQTT_USER_NAME = "MQTT_USERNAME";
    public static final String CONFIG_MQTT_PASSWORD = "MQTT_PASSWORD";
    public static final String CONFIG_DELAY_BETWEEN_MQTT = "DELAY_BETWEEN_MQTT";
    public static final String CONFIG_DELAY_BETWEEN_SAME_GLOBE = "DELAY_BETWEEN_SAME_GLOBE";

    // String channels
    public static final String CHANNEL_COLOUR = "colour";
    public static final String CHANNEL_PALETTES = "palettes";
    public static final String CHANNEL_PRESETS = "presets";
    public static final String CHANNEL_SAVE_PRESET = "savePreset";
    public static final String CHANNEL_PRESET_DURATION = "presetDuration";
    public static final String CHANNEL_PRESET_TRANS_TIME = "presetTransformTime";
    public static final String CHANNEL_PRESET_CYCLE = "presetCycle";

    public static final String CHANNEL_FX = "fx";
    public static final String CHANNEL_SPEED = "speed";
    public static final String CHANNEL_INTENSITY = "intensity";
    public static final String CHANNEL_SLEEP = "sleep";
}
