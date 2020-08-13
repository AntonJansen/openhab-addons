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
package org.openhab.binding.growatt.internal;

import static org.openhab.binding.growatt.internal.GrowattBindingConstants.CHANNEL_1;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.growatt.internal.responsetypes.ParserHelper;
import org.openhab.binding.growatt.internal.responsetypes.Plant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PlantHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 *
 *
 * content: {"back":{"data":[{"plantMoneyText":"209.7 (€)","plantName":"Anton
 * Jansen","plantId":"325704","isHaveStorage":"false","todayEnergy":"14 kWh","totalEnergy":"911.9
 * kWh","currentPower":"424.1 W"}],"totalData":{"currentPowerSum":"424.1 W","CO2Sum":"0
 * T","isHaveStorage":"false","eTotalMoneyText":"209.7 (€)","todayEnergySum":"14 kWh","totalEnergySum":"911.9
 * kWh"},"success":true}}
 *
 * @author Anton Jansen - Initial contribution
 */
@NonNullByDefault
public class PlantHandler extends BaseThingHandler implements PlantListener {

    private final Logger logger = LoggerFactory.getLogger(PlantHandler.class);

    private @Nullable PlantConfiguration config;

    public PlantHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand, channelUID: " + channelUID + " Command: " + command);
        if (CHANNEL_1.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    @Override
    public void initialize() {
        logger.debug("Start plant initializing!");
        config = getConfigAs(PlantConfiguration.class);
        updateStatus(ThingStatus.OFFLINE);

        // Register Plant with bridge, which will update the state once more information becomes available.
        Bridge bridge = getBridge();
        if ((bridge != null) && (bridge.getHandler() instanceof AccountBridgeHandler)) {
            AccountBridgeHandler accountBridgeHandler = (AccountBridgeHandler) bridge.getHandler();
            if (accountBridgeHandler != null) {
                accountBridgeHandler.registerPlantStatusListener(this);
            } else {
                logger.error("Handler not defined for bridge.");
            }
        } else {
            logger.error("Bridge is not right of the type. Found {} , expected AccountBridgeHandler",
                    bridge.getClass().getCanonicalName());
        }

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.

        // Example for background initialization:

        logger.debug("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void dispose() {
        logger.debug("Disposing plant handler for plant {}", getThing().getUID());
        Bridge bridge = getBridge();
        if ((bridge != null) && (bridge.getHandler() instanceof AccountBridgeHandler)) {
            AccountBridgeHandler accountBridgeHandler = (AccountBridgeHandler) bridge.getHandler();
            if (accountBridgeHandler != null) {
                accountBridgeHandler.deregisterPlantStatusListener(this);
            } else {
                logger.error("No handler for bridge found.");
            }
        } else {
            logger.error("Bridge is not right of the type.");
        }
    }

    @Override
    public Long getPlantId() {
        return Long.parseLong(getThing().getUID().getId());
    }

    @Override
    public void onPlantGone() {
        logger.debug("Plant {} is no longer available.", getPlantId());
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void onPlantStateChange(Plant plant) {
        updateStatus(ThingStatus.ONLINE);
        logger.debug("New state for plant {}", getPlantId());
        String[] fields = { "todayEnergy", "totalEnergy", "currentPower" };
        List<QuantityType<?>> dataValues;
        try {
            dataValues = ParserHelper.getDataValues(plant, fields);
            updateState(GrowattBindingConstants.CHANNEL_PLANT_TODAYENERGY, dataValues.get(0));
            updateState(GrowattBindingConstants.CHANNEL_PLANT_TOTALENERGY, dataValues.get(1));
            updateState(GrowattBindingConstants.CHANNEL_PLANT_CURRENTPOWER, dataValues.get(2));

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            logger.error("Cannot parse plant data for plant " + getPlantId(), e);
        }

        // Monetaire value
        try {
            Map<DecimalType, StringType> monValues = ParserHelper.getMonetaireValue(plant, "plantMoneyText");
            monValues.forEach((k, v) -> {
                updateState(GrowattBindingConstants.CHANNEL_PLANT_TOTALMONEY, k);
                updateState(GrowattBindingConstants.CHANNEL_PLANT_TOTALMONEYCURRENCY, v);
            });
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            logger.error("Cannot parse plant money data for plant " + getPlantId(), e);
        }

    }
}
