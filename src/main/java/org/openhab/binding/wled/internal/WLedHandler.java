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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WLedHandler} is responsible for handling commands and states, which are
 * sent to one of the channels or http replies back.
 *
 * @author Matthew Skinner - Initial contribution
 */

@NonNullByDefault
public class WLedHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HttpClient httpClient;
    private @Nullable ScheduledFuture<?> pollingFuture = null;
    private ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(1);
    private BigDecimal masterBrightness = new BigDecimal(0);
    private HSBType primaryColor = new HSBType();
    private HSBType secondaryColor = new HSBType();
    private WLedConfiguration config;
    private boolean hasWhite = false;

    public WLedHandler(Thing thing, HttpClient httpClient) {
        super(thing);
        this.httpClient = httpClient;
        config = getConfigAs(WLedConfiguration.class);
    }

    void sendGetRequest(String url) {
        Request request = httpClient.newRequest(config.address + url);
        request.timeout(10, TimeUnit.SECONDS);
        request.method(HttpMethod.GET);
        request.header(HttpHeader.ACCEPT_ENCODING, "gzip");

        logger.debug("Sending WLED GET:{}", url);
        String errorReason = "";
        try {
            ContentResponse contentResponse = request.send();
            if (contentResponse.getStatus() == 200) {
                processState(contentResponse.getContentAsString());
                return;
            } else {
                errorReason = String.format("WLED request failed with %d: %s", contentResponse.getStatus(),
                        contentResponse.getReason());
            }
        } catch (TimeoutException e) {
            errorReason = "TimeoutException: WLED was not reachable on your network";
        } catch (ExecutionException e) {
            errorReason = String.format("ExecutionException: %s", e.getMessage());
        } catch (InterruptedException e) {
            errorReason = String.format("InterruptedException: %s", e.getMessage());
        }
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorReason);
    }

    private HSBType parseToHSBType(String message, String element) {
        int startIndex = message.indexOf(element);
        if (startIndex == -1) {
            return new HSBType();
        }
        int endIndex = message.indexOf("<", startIndex + element.length());
        int r = Integer.parseInt(message.substring(startIndex + element.length(), endIndex));
        // look for second element
        startIndex = message.indexOf(element, endIndex);
        if (startIndex == -1) {
            return new HSBType();
        }
        endIndex = message.indexOf("<", startIndex + element.length());
        int g = Integer.parseInt(message.substring(startIndex + element.length(), endIndex));
        // look for third element called <cl>
        startIndex = message.indexOf(element, endIndex);
        if (startIndex == -1) {
            return new HSBType();
        }
        endIndex = message.indexOf("<", startIndex + element.length());
        int b = Integer.parseInt(message.substring(startIndex + element.length(), endIndex));
        return HSBType.fromRGB(r, g, b);
    }

    private void parseColours(String message) {
        primaryColor = parseToHSBType(message, "<cl>");
        updateState(CHANNEL_PRIMARY_COLOR, primaryColor);
        secondaryColor = parseToHSBType(message, "<cs>");
        updateState(CHANNEL_SECONDARY_COLOR, secondaryColor);
    }

    private void processState(String message) {
        logger.trace("WLED states are:{}", message);
        if (thing.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
        if (message.contains("<ac>0</ac>")) {
            updateState(CHANNEL_MASTER_CONTROLS, OnOffType.OFF);
        } else {
            masterBrightness = new BigDecimal(getValue(message, "<ac>")).divide(new BigDecimal(2.55),
                    RoundingMode.HALF_UP);
            updateState(CHANNEL_MASTER_CONTROLS, new PercentType(masterBrightness));
        }
        if (message.contains("<ix>0</ix>")) {
            updateState(CHANNEL_INTENSITY, OnOffType.OFF);
        } else {
            BigDecimal bigTemp = new BigDecimal(getValue(message, "<ix>")).divide(new BigDecimal(2.55),
                    RoundingMode.HALF_UP);
            updateState(CHANNEL_INTENSITY, new PercentType(bigTemp));
        }
        if (message.contains("<cy>1</cy>")) {
            updateState(CHANNEL_PRESET_CYCLE, OnOffType.ON);
        } else {
            updateState(CHANNEL_PRESET_CYCLE, OnOffType.OFF);
        }
        if (message.contains("<nl>1</nl>")) {
            updateState(CHANNEL_SLEEP, OnOffType.ON);
        } else {
            updateState(CHANNEL_SLEEP, OnOffType.OFF);
        }
        if (message.contains("<fx>")) {
            updateState(CHANNEL_FX, new StringType(getValue(message, "<fx>")));
        }
        if (message.contains("<sx>")) {
            BigDecimal bigTemp = new BigDecimal(getValue(message, "<sx>")).divide(new BigDecimal(2.55),
                    RoundingMode.HALF_UP);
            updateState(CHANNEL_SPEED, new PercentType(bigTemp));
        }
        if (message.contains("<fp>")) {
            updateState(CHANNEL_PALETTES, new StringType(getValue(message, "<fp>")));
        }
        if (message.contains("<wv>-1</wv>")) {
            hasWhite = false;
        } else {
            hasWhite = true;
        }
        parseColours(message);
    }

    /**
     *
     * @param hsb
     * @return WLED needs the letter h followed by 2 digit HEX code for RRGGBB
     */
    private String createColorHex(HSBType hsb) {
        String hex = Integer.toHexString(hsb.getRGB() & 0xffffff);
        while (hex.length() < 6) {
            hex = "0" + hex;
        }
        return "h" + hex;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            switch (channelUID.getId()) {
                case CHANNEL_MASTER_CONTROLS:
                    sendGetRequest("/win");
            }
            return;// no need to check for refresh below
        }
        logger.debug("command {} sent to {}", command, channelUID.getId());
        switch (channelUID.getId()) {
            case CHANNEL_MASTER_CONTROLS:
                if (command instanceof OnOffType) {
                    if (OnOffType.OFF.equals(command)) {
                        sendGetRequest("/win&TT=500&T=0");
                    } else {
                        sendGetRequest("/win&TT=2000&T=1");
                    }
                } else if (command instanceof IncreaseDecreaseType) {
                    if (IncreaseDecreaseType.INCREASE.equals(command)) {
                        if (masterBrightness.intValue() < 240) {
                            sendGetRequest("/win&TT=2000&A=~15"); // 255 divided by 15 = 17 levels
                        } else {
                            sendGetRequest("/win&TT=2000&A=255");
                        }
                    } else {
                        if (masterBrightness.intValue() > 15) {
                            sendGetRequest("/win&TT=2000&A=~-15");
                        } else {
                            sendGetRequest("/win&TT=2000&A=0");
                        }
                    }
                } else if (command instanceof HSBType) {
                    if ((((HSBType) command).getBrightness()) == PercentType.ZERO) {
                        sendGetRequest("/win&TT=500&T=0");
                    }
                    masterBrightness = new BigDecimal((((HSBType) command).getBrightness()).toString())
                            .multiply(new BigDecimal(2.55));
                    primaryColor = new HSBType(command.toString());
                    sendGetRequest(
                            "/win&TT=1000&FX=0&CY=0&CL=" + createColorHex(primaryColor) + "&A=" + masterBrightness);
                } else {// should only be PercentType left
                    masterBrightness = new BigDecimal(command.toString()).multiply(new BigDecimal(2.55));
                    sendGetRequest("/win&TT=2000&A=" + masterBrightness);
                }
                return;
            case CHANNEL_PRIMARY_COLOR:
                if (command instanceof OnOffType) {
                    logger.info("OnOffType commands should use masterControls channel");
                } else if (command instanceof HSBType) {
                    primaryColor = new HSBType(command.toString());
                    sendGetRequest("/win&CL=" + createColorHex(primaryColor));
                } else if (command instanceof IncreaseDecreaseType) {
                    logger.info("IncreaseDecrease commands should use masterControls channel");
                } else {// Percentype
                    primaryColor = new HSBType(primaryColor.getHue().toString() + ","
                            + primaryColor.getSaturation().toString() + ",command");
                    sendGetRequest("/win&CL=" + createColorHex(primaryColor));
                }
                return;
            case CHANNEL_SECONDARY_COLOR:
                if (command instanceof OnOffType) {
                    logger.info("OnOffType commands should use masterControls channel");
                } else if (command instanceof HSBType) {
                    secondaryColor = new HSBType(command.toString());
                    sendGetRequest("/win&C2=" + createColorHex(secondaryColor));
                } else if (command instanceof IncreaseDecreaseType) {
                    logger.info("IncreaseDecrease commands should use masterControls channel");
                } else {// Percentype
                    secondaryColor = new HSBType(secondaryColor.getHue().toString() + ","
                            + secondaryColor.getSaturation().toString() + ",command");
                    sendGetRequest("/win&C2=" + createColorHex(secondaryColor));
                }
                return;
            case CHANNEL_PALETTES:
                sendGetRequest("/win&FP=" + command);
                break;
            case CHANNEL_FX:
                sendGetRequest("/win&FX=" + command);
                break;
            case CHANNEL_SPEED:
                BigDecimal bigTemp = new BigDecimal(command.toString());
                if (OnOffType.OFF.equals(command)) {
                    bigTemp = new BigDecimal(0);
                } else if (OnOffType.ON.equals(command)) {
                    bigTemp = new BigDecimal(255);
                } else {
                    bigTemp = new BigDecimal(command.toString()).multiply(new BigDecimal(2.55));
                }
                sendGetRequest("/win&SX=" + bigTemp);
                break;
            case CHANNEL_INTENSITY:
                if (OnOffType.OFF.equals(command)) {
                    bigTemp = new BigDecimal(0);
                } else if (OnOffType.ON.equals(command)) {
                    bigTemp = new BigDecimal(255);
                } else {
                    bigTemp = new BigDecimal(command.toString()).multiply(new BigDecimal(2.55));
                }
                sendGetRequest("/win&IX=" + bigTemp);
                break;
            case CHANNEL_SLEEP:
                if (OnOffType.ON.equals(command)) {
                    sendGetRequest("/win&ND");
                } else {
                    sendGetRequest("/win&NL=0");
                }
                break;
            case CHANNEL_PRESETS:
                sendGetRequest("/win&PL=" + command);
                break;
            case CHANNEL_PRESET_DURATION:
                if (OnOffType.OFF.equals(command)) {
                    bigTemp = new BigDecimal(0);
                } else if (OnOffType.ON.equals(command)) {
                    bigTemp = new BigDecimal(255);
                } else {
                    // scale from 0.5 seconds to 1 minute
                    bigTemp = new BigDecimal(command.toString()).multiply(new BigDecimal(600)).add(new BigDecimal(500));
                }
                sendGetRequest("/win&PT=" + bigTemp);
                break;
            case CHANNEL_TRANS_TIME:
                if (OnOffType.OFF.equals(command)) {
                    bigTemp = new BigDecimal(0);
                } else if (OnOffType.ON.equals(command)) {
                    bigTemp = new BigDecimal(255);
                } else {
                    // scale from 0.5 seconds to 1 minute
                    bigTemp = new BigDecimal(command.toString()).multiply(new BigDecimal(600)).add(new BigDecimal(500));
                }
                sendGetRequest("/win&TT=" + bigTemp);
                break;
            case CHANNEL_PRESET_CYCLE:
                if (OnOffType.ON.equals(command)) {
                    sendGetRequest("/win&CY=1");
                } else {
                    sendGetRequest("/win&CY=0");
                }
                break;
        }
    }

    /**
     * Needs to be called from an ACTION which is not yet implemented
     *
     */
    public void savePreset(int presetIndex) {
        sendGetRequest("/win&PS=" + presetIndex);
    }

    void pollLED() {
        sendGetRequest("/win");
    }

    @Override
    public void initialize() {
        config = getConfigAs(WLedConfiguration.class);
        pollingFuture = threadPool.scheduleWithFixedDelay(this::pollLED, 1, config.pollTime, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        if (pollingFuture != null) {
            pollingFuture.cancel(true);
        }
    }

    /**
     * @return A string that starts after finding the element and terminates when it finds the first < char after the
     *         element.
     */
    static private String getValue(String message, String element) {
        int startIndex = message.indexOf(element);
        if (startIndex != -1) // It was found, as -1 means "not found"
        {
            int endIndex = message.indexOf('<', startIndex + element.length());
            if (endIndex != -1)// , not found so make second check
            {
                return message.substring(startIndex + element.length(), endIndex);
            }
        }
        return "";
    }
}
