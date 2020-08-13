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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link GrowattBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Anton Jansen - Initial contribution
 */
@NonNullByDefault
public class GrowattBindingConstants {

    public static final String BINDING_ID = "growatt";

    // List of all Thing Type UIDs
    public static final ThingTypeUID ACCOUNT_TYPE = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID PLANT_TYPE = new ThingTypeUID(BINDING_ID, "plant");

    // List of all Thing Types supported for discovert
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_FOR_DISCOVERY = Collections
            .unmodifiableSet(Stream.of(PLANT_TYPE).collect(Collectors.toSet()));

    // List of all Channel ids

    // Account
    public static final String CHANNEL_ACCOUNT_CURRENTPOWERSUM = "currentPowerSum";
    public static final String CHANNEL_ACCOUNT_CO2SUM = "CO2Sum";
    public static final String CHANNEL_ACCOUNT_TODAYENERGYSUM = "todayEnergySum";
    public static final String CHANNEL_ACCOUNT_TOTALENERGYSUM = "totalEnergySum";
    public static final String CHANNEL_ACCOUNT_TOTALMONEY = "totalMoney";
    public static final String CHANNEL_ACCOUNT_TOTALMONEYCURRENCY = "totalMoneyCurrency";

    // Plant
    public static final String CHANNEL_PLANT_CURRENTPOWER = "currentPower";
    public static final String CHANNEL_PLANT_TODAYENERGY = "todayEnergy";
    public static final String CHANNEL_PLANT_TOTALENERGY = "totalEnergy";
    public static final String CHANNEL_PLANT_TOTALMONEY = "plantMoney";
    public static final String CHANNEL_PLANT_TOTALMONEYCURRENCY = "plantMoneyCurrency";

    public static final String CHANNEL_1 = "channel_1";

}
